package dev.playchrono.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * Restricts the data folder to the current OS user: other accounts on the computer and services running as another
 * user can neither read the stats nor edit them. (The player themselves always can: it is their computer.)
 */
public final class Secure {
    private Secure() {}

    /** Owner-only on one file or folder (folder: also inherited by everything created inside later). */
    public static void ownerOnly(Path p) {
        try {
            PosixFileAttributeView posix = Files.getFileAttributeView(p, PosixFileAttributeView.class);
            if (posix != null) {
                posix.setPermissions(PosixFilePermissions.fromString(Files.isDirectory(p) ? "rwx------" : "rw-------"));
                return;
            }
            AclFileAttributeView acl = Files.getFileAttributeView(p, AclFileAttributeView.class);
            if (acl == null) return;
            UserPrincipal owner = Files.getOwner(p);
            List<AclEntry> entries = new ArrayList<>();
            AclEntry.Builder b = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if (Files.isDirectory(p)) b.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
            entries.add(b.build());
            try { // keep SYSTEM so Windows backup / indexing keeps working
                UserPrincipal system = p.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("SYSTEM");
                AclEntry.Builder s = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(system)
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class));
                if (Files.isDirectory(p)) s.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
                entries.add(s.build());
            } catch (IOException | UnsupportedOperationException ignored) { /* non-English or exotic systems */ }
            acl.setAcl(entries);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // best effort: never break the mod because of permissions
        }
    }

    /** Owner-only on a whole folder tree (run once at startup; new files inherit the folder's rules). */
    public static void ownerOnlyTree(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.forEach(Secure::ownerOnly);
        } catch (IOException | RuntimeException ignored) { /* best effort */ }
    }
}
