package app.nn.agent;

/** Single source of the app version; read by build.sh and app/build.gradle.kts. */
final class BuildInfo {
    static final String VERSION = "2.2.32";
    static final int VERSION_CODE = 201;

    private BuildInfo() {
    }
}
