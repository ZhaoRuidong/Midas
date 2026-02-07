# Midas

An IntelliJ IDEA plugin that generates weekly work reports from GitLab commits using AI-powered analysis.

## Features

- **GitLab Integration**: Integrates with GitLab REST API to fetch commits
- **Multi-Instance Support**: Connect to multiple GitLab instances (self-hosted or GitLab.com)
- **Cross-Project Reports**: Generate reports that span multiple GitLab projects
- **AI-Powered Analysis**: Intelligent summarization using AI (OpenAI/Claude/Zhipu AI)
- **Daily Notes Support**: Add manual daily notes to supplement commit data for richer reports
- **Multi-Language Reports**: Generate reports in Chinese or English
- **Scheduled Email Delivery**: Automatic email delivery of weekly reports
- **Customizable Templates**: Flexible report generation with customizable sections

## Requirements

- IntelliJ IDEA 2025.3 or later
- Java 21
- GitLab account (Personal Access Token required)
- OpenAI, Anthropic, or Zhipu AI API key for AI analysis
- SMTP server access for email delivery

## Installation

### From Source

```bash
# Clone the repository
git clone https://github.com/ZhaoRuidong/Midas.git
cd Midas

# Build the plugin
./gradlew buildPlugin

# Install the plugin
# In IntelliJ: File → Settings → Plugins → Gear Icon → Install Plugin from Disk
# Select: build/plugins/Midas-*.zip
```

### Development Mode

```bash
# Run IDE with plugin loaded
./gradlew runIde
```

## Configuration

### 1. GitLab Setup

Go to `Settings` → `Tools` → `Midas` → `GitLab Configuration`

**Generate Personal Access Token:**
1. Go to GitLab → User Settings → Access Tokens
2. Create a token with `read_api` and `read_repository` scopes
3. Add the token to Midas settings

**Configure GitLab Instances:**
- Instance Name: Display name (e.g., "GitLab.com", "Company GitLab")
- Instance URL: `https://gitlab.com` or your self-hosted URL
- Personal Access Token: Your generated token

### 2. AI Configuration

Go to `Settings` → `Tools` → `Midas` → `AI Configuration`

**OpenAI Setup:**
- Provider: OpenAI
- API Key: Your OpenAI API key
- Model: `gpt-4o`, `gpt-4o-mini`, or `gpt-3.5-turbo`
- Endpoint: `https://api.openai.com/v1`

**Claude Setup:**
- Provider: Anthropic
- API Key: Your Anthropic API key
- Model: `claude-sonnet-4-20250514` or similar
- Endpoint: `https://api.anthropic.com`

**Zhipu AI Setup (智谱AI):**
- Provider: Zhipu AI (智谱)
- API Key: Get from https://open.bigmodel.cn/usercenter/apikeys
- Model: `glm-4-plus`, `glm-4-air`, or `glm-4-flash`
- Endpoint: `https://open.bigmodel.cn/api/paas/v4`
- Uses OpenAI-compatible API format

**Custom Provider:**
- Provider: Custom
- API Key: Your custom provider's API key
- Model: Model name supported by your provider
- Endpoint: Your custom API endpoint URL
- Uses OpenAI-compatible format

### 3. Email Configuration

Go to `Settings` → `Tools` → `Midas` → `Email Configuration`

**SMTP Settings:**
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

1. Open the Midas tool window: `View` → `Tool Windows` → `Midas`
2. Click "Refresh" to fetch latest commits from GitLab
3. Click "Generate Report" to create a weekly report
4. Preview the report in the tool window
5. Click "Send Email" to send the report

### Automatic Email Delivery

1. Configure email settings as described above
2. Set the schedule to "Weekly (Monday 9:00 AM)" or custom
3. The plugin will automatically generate and send reports

### Report History

- View all previous reports in the tool window
- Click on any report to preview its content
- Re-send any report via email

### Daily Notes

Enhance your weekly reports with manual daily notes:

1. Open the Midas tool window: `View` → `Tool Windows` → `Midas`
2. Click on the "Daily Notes" tab
3. Add notes for each day to provide context that may not be captured in commit messages
4. Notes are integrated with commit data when generating reports
5. This helps create more comprehensive and accurate weekly reports

**Use cases for daily notes:**
- Document meetings and discussions
- Track non-code work (research, planning, code reviews)
- Add context for complex changes
- Note blockers or dependencies
- Record achievements not reflected in commits

### Report Language

Configure the output language in `Settings` → `Tools` → `Midas` → `General`:
- **Chinese**: Generate reports in Chinese (中文)
- **English**: Generate reports in English

The AI will analyze commits and generate the report in your selected language.

## Building with Gradle

```bash
# Build the plugin
./gradlew buildPlugin

# Run in development mode
./gradlew runIde

# Verify plugin
./gradlew verifyPlugin

# Run tests
./gradlew test
```

## Project Structure

```
midas/
├── src/main/kotlin/org/flymars/devtools/midas/
│   ├── core/              # GitLab API integration and data models
│   ├── analysis/          # AI analysis services
│   ├── report/            # Report generation
│   ├── email/             # Email sending
│   ├── config/            # Configuration management
│   ├── ui/                # User interface (Compose for Desktop)
│   └── data/              # Data models and storage
└── src/main/resources/
    └── META-INF/
        └── plugin.xml     # Plugin descriptor
```

## Report Sections

Each weekly report includes:

1. **本周概览** - Statistics (commits, lines changed, files)
2. **主要工作内容** - AI-generated work summary
3. **技术要点** - Technical highlights and achievements
4. **问题与解决方案** - Problems encountered and solutions
5. **代码质量分析** - Commit type distribution
6. **下周计划建议** - AI-suggested tasks for next week

## Data Storage

Commit data and reports are stored locally in your project:
```
your-project/.idea/midas/data.json
```

This file contains:
- GitLab instance configurations
- Project mappings
- Cached commit data
- Generated reports
- Statistics
- Daily notes

You can safely delete this file to reset all data.

## Troubleshooting

### Commits not being fetched

- Verify your GitLab Personal Access Token has the correct scopes
- Check the GitLab instance URL is correct
- Ensure you have access to the configured projects
- Check the IDE log: `Help` → `Show Log in Explorer`

### AI analysis fails

- Verify your API key is correct
- Check your internet connection
- Ensure the API endpoint is reachable
- Try the "Test Connection" button in settings
- Check if you have sufficient API credits

### Email sending fails

- Verify SMTP settings are correct
- For Gmail, use an App Password instead of your account password
- Check if your email provider requires app-specific passwords
- Test connection in settings
- Verify the recipient email addresses are valid

## Security

- **API Keys**: Stored securely in IntelliJ's encrypted storage
- **GitLab Tokens**: Stored securely using `PasswordSafe`
- **Email Passwords**: Stored securely using `PasswordSafe`
- **Data Privacy**: All data is stored locally; no data is sent to third parties except configured AI/SMTP services

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass (`./gradlew test`)
6. Submit a pull request

## Roadmap

- [ ] Support for GitHub and Azure DevOps
- [ ] Integration with Jira/Linear for ticket tracking
- [ ] Chart and visualization generation
- [ ] Team collaboration features (aggregate team reports)
- [ ] Web dashboard for viewing historical data
- [ ] Export reports as PDF
- [ ] Custom report templates with variables
- [ ] Support for multiple languages

## License

This project is licensed under the GNU General Public License v2.0 - see the [LICENSE](LICENSE) file for details.

## Support

- Issues: [GitHub Issues](https://github.com/ZhaoRuidong/Midas/issues)
- Discussions: [GitHub Discussions](https://github.com/ZhaoRuidong/Midas/discussions)

## Acknowledgments

- IntelliJ Platform SDK
- JetBrains Compose for Desktop UI
- OkHttp for HTTP client
- Angus Mail for email functionality
- GitLab REST API
- OpenAI API for AI analysis
- Anthropic Claude API for AI analysis
- Zhipu AI (智谱AI) API for AI analysis
