package org.flymars.devtools.midas.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.data.CommitInfo;
import org.flymars.devtools.midas.data.WeeklyReport;

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Stores and retrieves commit data using JSON files
 */
@Service(Service.Level.PROJECT)
public final class CommitStorage {
    private static final Logger LOG = Logger.getInstance(CommitStorage.class);

    private final Project project;
    private final ConfigManager configManager;
    private File storageFile;
    private File notesFile;
    private final Gson gson;
    private final List<CommitInfo> commitsCache = new CopyOnWriteArrayList<>();
    private final List<WeeklyReport> reportsCache = new CopyOnWriteArrayList<>();
    private final Map<LocalDate, org.flymars.devtools.midas.data.DailyNote> notesCache = new java.util.concurrent.ConcurrentHashMap<>();

    public CommitStorage(Project project) {
        this.project = project;
        this.configManager = ConfigManager.getInstance(project);
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();

        // Create storage directory using configured path
        this.storageFile = getStorageFile();
        this.notesFile = getNotesFile();
        ensureStorageDirectoryExists();

        loadData();
        loadNotes();
    }

    /**
     * Get storage file path based on configured storage path
     */
    private File getStorageFile() {
        String storagePath = configManager.getStoragePath();

        // Handle both relative and absolute paths
        File baseDir;
        if (storagePath.startsWith("/")) {
            // Absolute path
            baseDir = new File(storagePath);
        } else {
            // Relative path - resolve from project base
            baseDir = new File(project.getBasePath(), storagePath);
        }

        return new File(baseDir, "data.json");
    }

    /**
     * Get notes file path
     */
    private File getNotesFile() {
        String storagePath = configManager.getStoragePath();
        File baseDir;
        if (storagePath.startsWith("/")) {
            baseDir = new File(storagePath);
        } else {
            baseDir = new File(project.getBasePath(), storagePath);
        }
        return new File(baseDir, "daily-notes.json");
    }

    /**
     * Ensure storage directory exists
     */
    private void ensureStorageDirectoryExists() {
        File storageDir = storageFile.getParentFile();
        if (storageDir != null) {
            LOG.info("Checking storage directory: " + storageDir.getAbsolutePath());
            LOG.info("Directory exists: " + storageDir.exists());

            if (!storageDir.exists()) {
                boolean created = storageDir.mkdirs();
                LOG.info("Attempted to create directory. Success: " + created);
                LOG.info("Directory exists after mkdirs: " + storageDir.exists());

                if (!created && !storageDir.exists()) {
                    LOG.error("Failed to create storage directory: " + storageDir.getAbsolutePath());
                    LOG.error("Parent directory exists: " + (storageDir.getParentFile() != null && storageDir.getParentFile().exists()));
                }
            }
        } else {
            LOG.error("Storage directory is null!");
        }
    }

    /**
     * Save a commit to storage
     */
    public void saveCommit(CommitInfo commit) {
        commitsCache.add(commit);
        saveData();
        LOG.debug("Saved commit: " + commit.getHash());
    }

    /**
     * Get all commits
     */
    public List<CommitInfo> getAllCommits() {
        return new ArrayList<>(commitsCache);
    }

    /**
     * Get commits between two dates
     */
    public List<CommitInfo> getCommitsBetweenDates(LocalDateTime start, LocalDateTime end) {
        return commitsCache.stream()
                .filter(c -> !c.getTimestamp().isBefore(start) && !c.getTimestamp().isAfter(end))
                .sorted(Comparator.comparing(CommitInfo::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get commits for the current week
     */
    public List<CommitInfo> getCurrentWeekCommits() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.minusDays(now.getDayOfWeek().getValue() - 1).toLocalDate().atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusDays(7);
        return getCommitsBetweenDates(weekStart, weekEnd);
    }

    /**
     * Save a weekly report
     */
    public void saveReport(WeeklyReport report) {
        reportsCache.add(report);
        saveData();
        LOG.info("Saved weekly report for week starting: " + report.getWeekStart());
    }

    /**
     * Get all weekly reports
     */
    public List<WeeklyReport> getAllReports() {
        return new ArrayList<>(reportsCache);
    }

    /**
     * Get the most recent report
     */
    public Optional<WeeklyReport> getLatestReport() {
        return reportsCache.stream()
                .max(Comparator.comparing(WeeklyReport::getGeneratedAt));
    }

    /**
     * Get report for a specific week
     */
    public Optional<WeeklyReport> getReportForWeek(LocalDate weekStart) {
        return reportsCache.stream()
                .filter(r -> r.getWeekStart().equals(weekStart))
                .findFirst();
    }

    /**
     * Load data from storage file
     */
    private void loadData() {
        if (!storageFile.exists()) {
            LOG.info("No existing data file found, starting fresh");
            return;
        }

        try (Reader reader = new FileReader(storageFile)) {
            StorageData data = gson.fromJson(reader, StorageData.class);
            if (data != null) {
                if (data.commits != null) {
                    commitsCache.addAll(data.commits);
                }
                if (data.reports != null) {
                    reportsCache.addAll(data.reports);
                }
                LOG.info("Loaded " + commitsCache.size() + " commits and " +
                        reportsCache.size() + " reports from storage");
            }
        } catch (Exception e) {
            LOG.error("Error loading data from storage", e);
        }
    }

    /**
     * Save data to storage file
     */
    private void saveData() {
        try {
            // Ensure parent directory exists
            File parentDir = storageFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                LOG.info("Created storage directory: " + parentDir.getAbsolutePath() + " - Success: " + created);
            }

            StorageData data = new StorageData();
            data.projectPath = project.getBasePath();
            data.commits = commitsCache;
            data.reports = reportsCache;

            String json = gson.toJson(data);

            try (Writer writer = new FileWriter(storageFile)) {
                writer.write(json);
            }

            // Refresh Virtual File if it exists
            VirtualFile vFile = VfsUtil.findFileByIoFile(storageFile, true);
            if (vFile != null) {
                vFile.refresh(false, false);
            }

            LOG.debug("Data saved to " + storageFile.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error saving data to storage", e);
        }
    }

    /**
     * Clear all data (for testing or reset)
     */
    public void clearAllData() {
        commitsCache.clear();
        reportsCache.clear();
        notesCache.clear();
        saveData();
        saveNotes();
        LOG.info("Cleared all data");
    }

    // ========== Daily Notes Management ==========

    /**
     * Save or update a daily note
     */
    public void saveNote(org.flymars.devtools.midas.data.DailyNote note) {
        if (note == null || note.getDate() == null) {
            LOG.warn("Cannot save note: note or date is null");
            return;
        }

        notesCache.put(note.getDate(), note);
        saveNotes();
        LOG.info("Saved note for date: " + note.getDate());
    }

    /**
     * Get a note for a specific date
     */
    public org.flymars.devtools.midas.data.DailyNote getNote(LocalDate date) {
        return notesCache.get(date);
    }

    /**
     * Get all notes
     */
    public List<org.flymars.devtools.midas.data.DailyNote> getAllNotes() {
        return new ArrayList<>(notesCache.values());
    }

    /**
     * Get notes for a date range (inclusive)
     */
    public List<org.flymars.devtools.midas.data.DailyNote> getNotesInRange(LocalDate startDate, LocalDate endDate) {
        return notesCache.entrySet().stream()
                .filter(e -> !e.getKey().isBefore(startDate) && !e.getKey().isAfter(endDate))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(org.flymars.devtools.midas.data.DailyNote::getDate))
                .collect(Collectors.toList());
    }

    /**
     * Get notes for the current week
     */
    public List<org.flymars.devtools.midas.data.DailyNote> getNotesForCurrentWeek() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1); // Monday
        LocalDate weekEnd = weekStart.plusDays(6); // Sunday
        return getNotesInRange(weekStart, weekEnd);
    }

    /**
     * Delete a note for a specific date
     */
    public void deleteNote(LocalDate date) {
        if (notesCache.remove(date) != null) {
            saveNotes();
            LOG.info("Deleted note for date: " + date);
        }
    }

    /**
     * Load notes from file
     */
    private void loadNotes() {
        if (!notesFile.exists()) {
            LOG.info("Notes file does not exist: " + notesFile.getAbsolutePath());
            return;
        }

        try (Reader reader = new FileReader(notesFile)) {
            Type listType = new TypeToken<List<org.flymars.devtools.midas.data.DailyNote>>(){}.getType();
            List<org.flymars.devtools.midas.data.DailyNote> notes = gson.fromJson(reader, listType);

            if (notes != null) {
                notesCache.clear();
                for (org.flymars.devtools.midas.data.DailyNote note : notes) {
                    notesCache.put(note.getDate(), note);
                }
                LOG.info("Loaded " + notes.size() + " daily notes from " + notesFile.getAbsolutePath());
            }
        } catch (Exception e) {
            LOG.error("Error loading notes from file", e);
        }
    }

    /**
     * Save notes to file
     */
    private void saveNotes() {
        try {
            File parentDir = notesFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            List<org.flymars.devtools.midas.data.DailyNote> notes = new ArrayList<>(notesCache.values());
            String json = gson.toJson(notes);

            try (Writer writer = new FileWriter(notesFile)) {
                writer.write(json);
            }

            VirtualFile vFile = VfsUtil.findFileByIoFile(notesFile, true);
            if (vFile != null) {
                vFile.refresh(false, false);
            }

            LOG.debug("Saved " + notes.size() + " notes to " + notesFile.getAbsolutePath());
        } catch (Exception e) {
            LOG.error("Error saving notes to file", e);
        }
    }

    /**
     * Reload storage path and save current data to new location
     * Call this when the storage path configuration changes
     *
     * @param oldStoragePath the old storage path (can be null if first time setup)
     */
    public void reloadStoragePath(String oldStoragePath) {
        LOG.info("Reloading storage path");
        LOG.info("Old storage path: " + oldStoragePath);
        LOG.info("New storage path: " + configManager.getStoragePath());

        // Get old storage file
        File oldStorageFile = this.storageFile;
        LOG.info("Old storage file: " + (oldStorageFile != null ? oldStorageFile.getAbsolutePath() : "null"));

        // Get new storage file path
        File newStorageFile = getStorageFile();
        LOG.info("New storage file: " + newStorageFile.getAbsolutePath());

        // Check if path actually changed
        if (oldStorageFile != null && oldStorageFile.getAbsolutePath().equals(newStorageFile.getAbsolutePath())) {
            LOG.info("Storage path unchanged, no action needed");
            return;
        }

        // Update storage file reference to new location
        this.storageFile = newStorageFile;
        this.notesFile = getNotesFile();

        // Create new directory and save current cache data
        File newDir = newStorageFile.getParentFile();
        if (newDir != null && !newDir.exists()) {
            boolean created = newDir.mkdirs();
            LOG.info("Created new storage directory: " + newDir.getAbsolutePath() + " - Success: " + created);
            if (!created && !newDir.exists()) {
                LOG.error("Failed to create storage directory: " + newDir.getAbsolutePath());
            }
        }

        // Save current cache data to new location
        LOG.info("Saving current cache data to new location...");
        LOG.info("Cache contains " + commitsCache.size() + " commits and " + reportsCache.size() + " reports");
        saveData();

        // Verify file was created
        if (newStorageFile.exists()) {
            LOG.info("Successfully saved data to new location. File size: " + newStorageFile.length() + " bytes");
        } else {
            LOG.error("Failed to create file at new location: " + newStorageFile.getAbsolutePath());
        }

        // Note: GitLab project cache will be automatically rebuilt when needed
        LOG.info("Storage path reload completed");
    }

    /**
     * Storage data model
     */
    private static class StorageData {
        String projectPath;
        List<CommitInfo> commits;
        List<WeeklyReport> reports;
    }

    /**
     * Gson adapter for LocalDateTime
     */
    private static class LocalDateTimeAdapter implements com.google.gson.JsonSerializer<LocalDateTime>,
            com.google.gson.JsonDeserializer<LocalDateTime> {

        @Override
        public com.google.gson.JsonElement serialize(LocalDateTime src, java.lang.reflect.Type typeOfSrc,
                                                       com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(src.toString());
        }

        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT,
                                         com.google.gson.JsonDeserializationContext context) {
            return LocalDateTime.parse(json.getAsString());
        }
    }
}
