# 📰 Server News List
### Keep your players informed with a modern, feature-rich in-game news system!

Tired of players missing important updates because the chat moves too fast? Server News List adds a beautiful, customizable in-game newspaper. Admins write the news via simple config files, and players read it in a dedicated GUI with images, ratings, and more!



# 🛠️ How to use (For Admins)
Once the mod is installed on the server, the folder structure is automatically generated at:
config/servernewslist/<Your_Server_Name>/


<details>
<summary>Creating a News Post</summary>

- Go to the news folder.

- Create a new folder. The folder name will be the Title of your news.

- Inside, create a news.txt file and write your text. Use \1, \2, etc., to insert images in the text.

- Create a properties.txt file to set the release time, news type, and if players can rate it:

```
properties
# Publishing GMT/UTC time (Day.Month.Year Hour:Minute)
time=25.12.2026 18:00

# News Item type: 1(dark), 2(green), 3(blue), 4(red)
type=1

# Can players increase/decrease news item rating (true/false)
allow_rating=true
```

- Drop your .webp images (named like 1.webp , 2.webp etc.) into the folder.

</details>






<details>
<summary>Scheduling News</summary>

Put the folder into the postponed directory instead of news. Once the GMT time in properties.txt is reached, the mod will automatically move it to news and make it visible!

</details>




<details>
<summary>GameRules</summary>

- /gamerule servernewslist:news_lifetime_hours <number> - Auto-delete news after X hours (Default: 336, set to 0 to disable).

- /gamerule servernewslist:safe_delete <true/false> - If true, expired news goes to the deleted folder instead of being permanently deleted.

</details>

