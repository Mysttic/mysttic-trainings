# Trainings on the Mysttic site

Everything needed to build `mysttic-trainings/` in `Mysttic/Mysttic`, the repository behind
<https://mysttic.github.io/Mysttic/>. The page is English, like `chatt/`; the app itself is Polish and
that is worth saying out loud on the page rather than hiding.

The app repository is public since 1.1.0 (D-48), but releases still reach people through the site:
the APK and a two-line release note land there, copied
there by the release workflow (`.github/workflows/wydanie.yml`) right after a release is published,
in the closing steps of its `wydanie` job.

---

## The download contract

Three values are read by more than one program. The page reads them, the updater inside the app reads
them, and the release workflow writes them. Changing any of them breaks something beyond the page.

| What | Value |
|---|---|
| Tag on the site | `mysttic-trainings-v<version>`, e.g. `mysttic-trainings-v0.3.1` |
| File | `mysttic-trainings-<version>-android.apk`, e.g. `mysttic-trainings-0.3.1-android.apk` |
| Release title | `Treningi <version>` |
| Release notes | `Treningi <version>`, blank line, `https://mysttic.github.io/Mysttic/mysttic-trainings/` |

The prefix is what tells the projects apart: `GET /releases` lists the releases of the whole repository,
so without it the app would take Chatt's version for its own. Everything that reads releases keeps the
tags made of the prefix and a version right after it (`X.Y.Z` for the app, a plain number for the
exercise database) and takes the highest version, not the first of the list: the list follows the date
of the commit each tag points at, not the order of publication, and a copy that failed halfway leaves
an older release behind. A file still being uploaded (`state` other than `uploaded`) counts as no
release yet. The full rules for apps sit in `RELEASES.md` in the site repository.

### The configuration block

The only per-project JavaScript on the page, at the end of `<body>`:

```html
<script src="../assets/releases.js"></script>
<script>
initDownloads({
  tagPrefix: 'mysttic-trainings-v',
  files: [
    { id: 'apk', match: /-android\.apk$/ }
  ]
});

initDownloads({
  tagPrefix: 'mysttic-trainings-baza-v',
  ids: { version: 'database-version', date: 'database-date' },
  files: [],
  notYet: 'comes with the app',
  strings: { notReleased: 'comes with the app' }
});
</script>
```

It needs `release-version` and `release-date` on the version line, and a `download-apk` button next to
a `size-apk` line. One file, so one entry: there is no desktop build and there is not going to be one.
The second call fills the exercise database label (`database-version`, `database-date`) from the same
request; see [Exercise database releases](#exercise-database-releases-d-47).

### The links

The button address is filled from the API at every visit, so nothing has to be committed when a release
goes out. Resolved for the current release it comes out as:

```
https://github.com/Mysttic/Mysttic/releases/download/mysttic-trainings-v0.3.1/mysttic-trainings-0.3.1-android.apk
```

Two addresses are safe to write into the markup by hand, because neither carries a version number:

- <https://github.com/Mysttic/Mysttic/releases> — the fallback `releases.js` puts on the buttons when
  the API cannot be reached or the hourly limit is spent.
- <https://mysttic.github.io/Mysttic/mysttic-trainings/> — the page itself, which is also what the release
  notes and the app's update card point at.

Do not link `releases/latest`: it answers for the whole repository, so the day another project
publishes, that link starts handing out somebody else's file.

---

## Page copy

Ready to paste. British spelling, same register as the Chatt page.

### Head

```
Title:       Trainings, a home workout plan that builds itself
Description: Tell it your height, weight, goal, equipment and how long you have. It lays out a
             30-day cycle and shows you one workout a day. No account, no ads, nothing leaves
             the phone.
```

### Hero

> **Trainings**
>
> A home workout plan that builds itself.
>
> You answer six questions once. The app lays out a cycle, then shows one workout a day and counts you
> through it: reps, seconds, rests, a beep before every set. When the cycle ends it writes the next one
> from what you actually did.
>
> Android 8.0 or newer · about 6 MB · Polish interface

### Download section

> **Download**
>
> `release-version` · `release-date`
>
> Exercise database: `database-version` · `database-date`
>
> [ Download for Android ] `size-apk`
>
> The file is signed and installs over an earlier version without touching your history. Android will
> ask you to allow installing from this source; that prompt appears because the app is not distributed
> through the Play Store, and you can revoke the permission straight after.

### Features

> **One workout at a time**
> No catalogue to browse and no daily decision. The home screen holds today's workout and a Start
> button, and nothing else.
>
> **A plan that fits your day**
> Say fifteen minutes or forty-five; the generator hits the number within a minute by trading off
> rounds, sets and rests. It also only picks exercises you have the equipment for, and offers a
> substitute for the ones you do not.
>
> **It counts, you move**
> Countdown, reps, rests, a spoken cue for the next exercise. The numbers are sized to be read from a
> metre and a half away, and the volume key confirms a set so you do not have to reach for the screen.
>
> **A queue, not a calendar**
> Miss a day and nothing is lost: the workout waits its turn. The streak survives a rest day, because
> a rest day was part of the plan.
>
> **It notices you got stronger**
> Hit every target in a workout and the app offers to raise them. One tap applies it. At the end of a
> cycle it asks how the whole thing went and takes the answer into the next one.
>
> **Your history stays yours**
> Every session, set and note lives in a database on the phone. Export it to a file whenever you like,
> and import it back on another phone.

### Privacy

> The app has no account, no analytics and no ads, and it reads nothing about you.
>
> It reaches the network in exactly one place: once a day, when you turn the switch on, it asks GitHub
> for the newest version number of this project and of its exercise database. That request carries no
> identifiers: GitHub sees an IP address and a timestamp, the same as any HTTPS server. Turn the switch
> off and the app never leaves the device.
>
> Workouts can optionally be written to Health Connect. That is off until you turn it on, it only ever
> writes, and it never reads anything back.

### Requirements

> Android 8.0 (API 26) or newer. Around 6 MB installed, plus your own history. No Google account and
> no Play Store.

### Footer

> Built by Mysttic · [More projects](../) · [Privacy](#privacy)

---

## Images

Screenshots live in the app repository under `docs/screenshots/`, at 1280 × 2856. Scale them to 720 px
wide for the page, keep the aspect ratio, and put them in `mysttic-trainings/img/`:

| Site file | Source | Caption |
|---|---|---|
| `today.png` | `dzis.png` | Today: one workout, one button |
| `player.png` | `player.png` | The player, mid set |
| `plan.png` | `plan.png` | The cycle as a queue |
| `wizard.png` | `kreator.png` | Four questions, one plan |
| `history.png` | `historia.png` | Volume by week and a twelve-week calendar |
| `today-light.png` | `dzis-jasny.png` | The light theme, for the gallery caption about both themes |

The app icon for `mysttic-trainings/img/icon.svg` is in the app repository at `docs/site/icon.svg`: a dumbbell
in `#2A0B00` on a `#FF6A21` square, the same shape the launcher uses.

## The card on the front page

`index.html` at the root of the site repository holds one card per project. The Trainings card:

> **Trainings**
> A home workout plan that builds itself. Android, Polish interface.
> [Open](mysttic-trainings/)

---

## After the first release

- [ ] The version line shows the number, not "see GitHub" — if it says that, the API call failed or no
      release carries the `mysttic-trainings-v` prefix.
- [ ] The button downloads `mysttic-trainings-<version>-android.apk` and the size next to it matches the file.
- [ ] `https://github.com/Mysttic/Mysttic/releases` lists exactly one `mysttic-trainings-v` release; older ones
      are deleted by the workflow, together with their tags.
- [ ] The release notes on the site hold the number and the page address, nothing else. That is the
      contract the app's update card reads; the full changelog stays with the release in the code repository.
- [ ] In the app: Settings, Updates, "Check now" finds the release and the card shows the same number.

## Exercise database releases (D-47)

The exercise database has its own releases in the same repository: `mysttic-trainings-baza-v<number>` with
`mysttic-trainings-baza-<number>.zip` and the manifest `mysttic-trainings-baza-<number>.json`, published by
`.github/workflows/baza.yml` and never marked "Latest". Only the app downloads them, so the page has no
tile and no button for the database, just a label under the app's version line: "Exercise database:"
with the number and the date of the newest database release, filled by the second `initDownloads` call
from the same request. The number is the one under "Wersja bazy ćwiczeń" in `VERSION.md`, and the GitHub
release behind it carries the `[Baza <number>]` section of `CHANGELOG.md` as its notes. With no database
release on the site the label says "comes with the app", which
is true: the app always carries a database of its own. The app's line is unaffected, because
`mysttic-trainings-baza-v` does not start with `mysttic-trainings-v`.

- [ ] `https://github.com/Mysttic/Mysttic/releases` lists exactly one `mysttic-trainings-baza-v` release
      next to the app release; older database releases are deleted by the workflow.
- [ ] The version line on the page still shows the app version, and the label under it the database
      number and date.
