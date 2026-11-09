package app.nn.agent;

/** Single source of the app version; read by build.sh and app/build.gradle.kts. */
final class BuildInfo {
    static final String VERSION = "2.2.35";
    static final int VERSION_CODE = 204;

    private BuildInfo() {
    }
}
