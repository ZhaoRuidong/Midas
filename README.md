# GodHand

A work assistant born to serve the beasts of burden; he's willing to do anything you can think of.

## Configuration

### 1. AI Configuration

Go to `Settings` → `Tools` → `Second Hand` → `AI Configuration`

**OpenAI Setup:**
- Provider: OpenAI
- API Key: Your OpenAI API key
- Model: `gpt-3.5-turbo` or `gpt-4`
- Endpoint: `https://api.openai.com/v1`

**Claude Setup:**
- Provider: Claude
- API Key: Your Anthropic API key
- Model: `claude-3-opus` or `claude-3-sonnet`
- Endpoint: `https://api.anthropic.com`

### 2. Email Configuration

Go to `Settings` → `Tools` → `Second Hand` → `Email Configuration`

- SMTP Host: Your SMTP server (e.g., `smtp.gmail.com`)
- SMTP Port: `587` (TLS) or `465` (SSL)
- Username: Your email address
- Password: Your email password or app-specific password
- From Email: Your sender email
- To Emails: Comma-separated recipient list
- Schedule: Choose when to send reports

**Gmail Setup:**
1. Enable 2-Factor Authentication
2. Generate an App Password: Google Account → Security → App Passwords
3. Use the app password in the plugin

## Usage

### Manual Report Generation

1. Open the Second Hand tool window: `View` → `Tool Windows` → `Second Hand`
2. Click "Generate Report" to create a weekly report
3. Preview the report in the tool window
4. Click "Send Email" to send the report

### Automatic Email Delivery

1. Configure email settings as described above
2. Set the schedule to "Weekly (Monday 9:00 AM)" or custom
3. The plugin will automatically generate and send reports

### Report History

- View all previous reports in the tool window
- Click on any report to preview its content
- Re-send any report via email

## Data Storage

Commit data is stored locally in your project:
```
your-project/.idea/second-hand/data.json
```

This file contains:
- All captured commits
- Generated reports
- Statistics

You can safely delete this file to reset all data.

## Report Sections

Each weekly report includes:

1. **本周概览** - Statistics (commits, lines changed, files)
2. **主要工作内容** - AI-generated work summary
3. **技术要点** - Technical highlights and achievements
4. **问题与解决方案** - Problems encountered and solutions
5. **代码质量分析** - Commit type distribution
6. **下周计划建议** - AI-suggested tasks for next week

## Building with Gradle (Recommended)

```bash
# Build the plugin
./gradlew buildPlugin

# Run in development mode
./gradlew runIde

# Verify plugin
./gradlew verifyPlugin

# Publish to marketplace
./gradlew publishPlugin
```

## Building with Maven (Alternative)

```bash
# Build the plugin
mvn clean package

# Run tests
mvn test
```

Note: Gradle is the recommended build tool for IntelliJ Platform plugins.

## Development

### Project Structure

```
second-hand/
├── src/main/java/com/yourname/commitreporter/
│   ├── core/              # Git tracking and storage
│   ├── analysis/          # AI analysis services
│   ├── report/            # Report generation
│   ├── email/             # Email sending
│   ├── config/            # Configuration management
│   ├── ui/                # User interface
│   └── data/              # Data models
└── src/main/resources/
    └── META-INF/
        └── plugin.xml     # Plugin descriptor
```

### Adding New Features

1. **New AI Provider**: Extend `AIAnalyzerService`
2. **Custom Report Template**: Modify `ReportTemplate`
3. **Additional Statistics**: Extend `Statistics` class
4. **UI Improvements**: Update panels in `ui/` package

## Troubleshooting

### Commits not being recorded

- Check if commit tracking is enabled in settings
- Verify the branch is not in the ignored branches list
- Check the IDE log: `Help` → `Show Log in Explorer`

### AI analysis fails

- Verify your API key is correct
- Check your internet connection
- Ensure the API endpoint is reachable
- Try the "Test Connection" button in settings

### Email sending fails

- Verify SMTP settings are correct
- For Gmail, use an App Password instead of your account password
- Check if your email provider requires app-specific passwords
- Test connection in settings

## Security

- **API Keys**: Stored securely in IntelliJ's encrypted storage
- **Email Passwords**: Stored securely using `PasswordSafe`
- **Data Privacy**: All data is stored locally; no data is sent to third parties except configured AI/SMTP services

## License

MIT License - see LICENSE file for details

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## Roadmap

- [ ] Support for multiple languages (English, Chinese, Japanese)
- [ ] Integration with Jira/Linear for ticket tracking
- [ ] Chart and visualization generation
- [ ] Team collaboration features (aggregate team reports)
- [ ] Web dashboard for viewing historical data
- [ ] Export reports as PDF
- [ ] Custom report templates with variables

## Support

- Issues: [GitHub Issues](https://github.com/yourname/second-hand/issues)
- Discussions: [GitHub Discussions](https://github.com/yourname/second-hand/discussions)

## Acknowledgments

- IntelliJ Platform SDK
- OkHttp for HTTP client
- Gson for JSON processing
- Jakarta Mail for email functionality
