# ModTheSpire #
ModTheSpire is a tool to load external mods for Slay the Spire without modifying the base game files.

## Usage ##
### Installation ###
1. Download the latest [Release](https://github.com/kiooeht/ModTheSpire/releases).
2. Copy `ModTheSpire.jar` to your Slay the Spire install directory.
    * For Windows, copy `MTS.cmd` to your Slay the Spire install directory.
    * For Linux, copy `MTS.sh` to your Slay the Spire install directory and make it executable.
3. Create a `mods` directory. Place mod JAR files into the `mods` directory.

### Running Mods ###
1. Run ModTheSpire.
    * For Windows, run `MTS.cmd`.
   * For Linux, run `MTS.sh`. If the game has no bundled Java 8, the script checks `JAVA_HOME` and then `PATH` for Java 8.
    * Or run `ModTheSpire.jar` with Java 8.
2. Select the mod(s) you want to use.
3. Press 'Play'.

Drag the divider beside **Description** to resize it. Select a mod and use **Add note…** to save a local note shared across profiles.

Use **Merge profiles…** to add saved selections from one or more profiles to the current selection. Mods already selected keep their order and duplicates are skipped. Playing or switching profiles saves the resulting selection to the current profile.

---

## For Modders ##
### Requirements ###
* JDK 8
* Maven

### General ###
* ModTheSpire automatically sets the Settings.isModded flag to true, so there is no need to do that yourself.
* [Wiki](https://github.com/kiooeht/ModTheSpire/wiki/SpirePatch)

### Building ###
1. Run `mvnw package`

---

## Changelog ##
See [CHANGELOG](CHANGELOG.md)

## Contributors ##
* kiooeht - Original author
* t-larson - Multi-loading, mod initialization, some UI work
* test447 - Some launcher UI work, Locator
* reckter - Maven setup
* FlipskiZ - Mod initialization
* pk27602017 - UTF-8 support in ModInfo
* bwwq - UI theme
