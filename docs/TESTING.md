# Testing Inama on an Android phone or tablet (no computer needed)

You need a GitHub account, a web browser, and an Android 8.0+ phone or tablet. GitHub Actions builds the app and GitHub Codespaces runs the server, both in the cloud.

## 1. Put the code on GitHub

1. On github.com, tap **+ › New repository**. Name it (e.g. `inama`), tick **Add a README file**, and tap **Create repository**.
2. On the repository page, tap **Code › Codespaces › Create codespace on main**. VS Code opens in the browser.
3. Upload `Inama-Android-MVP.zip`: in the Explorer panel, long-press (or right-click) the empty area and choose **Upload…**.
4. In the Terminal (menu › Terminal › New Terminal), run:
   ```bash
   unzip -q Inama-Android-MVP.zip
   cp -a inama-mvp/. .
   rm -rf inama-mvp Inama-Android-MVP.zip
   git add -A && git commit -m "Inama Android MVP" && git push
   ```
   The files must sit at the top of the repository (`.github/`, `android/`, `server/`, …), or the build won't start.

## 2. Build the APK (GitHub Actions)

5. Open the repository's **Actions** tab. If GitHub asks, enable workflows. The **Android** workflow starts by itself after the push. It can also be started by hand with **Run workflow**. The first run takes about 10 minutes.
6. When it shows a green tick, go to the repository home page › **Releases** › **Inama debug build** and tap `inama-debug.apk`. The link is `https://github.com/<you>/<repo>/releases/download/latest-debug/inama-debug.apk`. The APK is also attached to each run as the `inama-debug-apk` artifact (a zip).
7. If the run fails (red ✗): open the run, tap the failed step, and copy its last lines to whoever is fixing it.

## 3. Install

8. Open the downloaded `inama-debug.apk`. Android will ask to allow installs from your browser (**Settings › Install unknown apps**). Allow it, go back, and tap **Install**. If Play Protect warns about an unknown developer, choose **Install anyway**. This is a debug build.

## 4. Test offline (no server)

The app tries the server first. When it can't reach it, it works on its own:

| # | Do | Expect |
|---|---|---|
| A | **Onboarding**: Start → English → Continue → Next through the intro → I agree, continue → phone `788 123 456` → Send code | After up to ~10 s: a banner "Demo mode: your code is 123456". Tap **Use it** → Confirm |
| | Name + district → Continue → first field: name, **Maize**, 10 ares, **Yes, planted**, tap + a few weeks → Save field → Go to my farm | Home: greeting, weather card ("Sample forecast"), today's priority, your field card, quick actions |
| B | **Photo check with a sample**: Check a sick plant → **Samples** → *Cassava leaf with spots* | Review screen: "Clear photo", Cassava selected. Tick *Brown spots on leaves* and one more sign → **Check my plant** |
| | | Progress steps, then the result: headline, confidence in words with a reason, *What's happening / Why it matters / What to do*, evidence, sources, and "Answered on your phone" at the bottom |
| | **Add these steps to my tasks** | Green banner "N steps added" → **See tasks** opens Tasks |
| C | **Blurry photo**: Samples → *Blurry bean leaf* | Warning with retake tips and **Check my plant** disabled → **Use this photo anyway** → "Not sure yet" result with safe steps only |
| | **Ask a farmer promoter to check** | Case screen: Sent. Stay on it (it refreshes itself): Seen after ~20 s, then a reply after ~90 s that you can Listen to |
| D | **Healthy leaf**: Samples → *Healthy cassava leaf* | "Good news" result |
| E | **Real camera**: Check a sick plant → allow camera → photograph a leaf | Same flow with your photo |
| F | **Ask**: centre Ask button in the bottom bar → big microphone → allow microphone → say "My maize has holes in the leaves" (or type it) | Question bubble, then an answer card with a **Listen** button and "Add these steps to my tasks" |
| G | **Tasks** tab | Tick a task → it moves to **Done**; add your own task |
| H | **Farm** tab → your field | Stage track, days to harvest, tips for this stage; **Edit** and **Delete** work |
| I | Home → weather card; Home → **Learn** → a lesson → Listen; Home → **My history** (All / Photo checks / Questions) | Content loads; history lists your checks and questions |
| J | **Me › Settings**: Text size *Large*, **High contrast**, AI *Phone only* | Text grows, colours strengthen |
| K | Turn on **Airplane mode** | Offline banner on Home and Ask; photo checks and questions still answer |
| L | **Settings › Reset all data** | Back to the start screen |

Voice questions need Google's speech service on the device. If it isn't available, the app says so and you can type.

## 5. Test with the server and AI (optional)

9. In the Codespace terminal:
   ```bash
   cd server
   cp .env.example .env      # optional: add GEMINI_API_KEY (aistudio.google.com) and WEATHER_API_KEY (openweathermap.org)
   npm run dev:inama         # no npm install needed
   ```
10. Open the **Ports** tab. Long-press port **5055** › **Port visibility › Public**, then copy its address (`https://…-5055.app.github.dev`).
11. In the tablet's browser, open `<address>/api/v1/health`. You should see `"status":"ok"` (tap Continue if GitHub shows a notice page first).
12. In the app: **Me › Settings › Server**. Paste `<address>/api/v1` → **Test** (should say *Connected*) → **Save**.
13. If you signed in offline earlier, **Settings › Sign out**, then go through sign-in again. The code now comes from the server, and the banner shows a different code. Your fields and history stay. At the field step you can tap *Skip for now*.
14. Do a photo check. The result footer now says **Answered by the Inama server · gemini-…** (or `· inama-kb` without a Gemini key). With `WEATHER_API_KEY`, the weather is live.

A Codespace stops after about 30 idle minutes, and the server forgets its data when restarted. After a restart, run `npm run dev:inama` again, set the port to Public again, and sign out and back in. Turn the tablet's Wi-Fi off to watch the app fall back to on-device answers.
