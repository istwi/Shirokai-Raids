import java.nio.file.Path;
import net.fabricmc.installer.LoaderVersion;
import net.fabricmc.installer.server.ServerInstaller;
import net.fabricmc.installer.util.InstallerProgress;

/** Use the official installer's local-loader API with the repository's exact versions. */
class InstallPinnedFabric {
    public static void main(String[] args) throws Exception {
        ServerInstaller.install(Path.of(args[0]), new LoaderVersion(Path.of(args[1])), "1.21.1", InstallerProgress.CONSOLE);
    }
}
