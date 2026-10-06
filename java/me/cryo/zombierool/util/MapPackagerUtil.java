package me.cryo.zombierool.util;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MapPackagerUtil {

    // Dossiers à ignorer totalement lors de la compression
    private static final Set<String> EXCLUDED_FOLDERS = Set.of(
            "playerdata",
            "stats",
            "advancements"
    );

    // Fichiers spécifiques à ignorer
    private static final Set<String> EXCLUDED_FILES = Set.of(
            "session.lock",
            "level.dat_old"
    );

    public static boolean zipMapClientSide(String worldFolderName) {
        try {
            File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
            File worldDir = new File(savesDir, worldFolderName);
            
            if (!worldDir.exists() || !worldDir.isDirectory()) {
                return false;
            }

            File exportsDir = new File(Minecraft.getInstance().gameDirectory, "zombierool_exports");
            if (!exportsDir.exists()) {
                exportsDir.mkdirs();
            }

            File zipFile = new File(exportsDir, worldFolderName + ".zip");
            if (zipFile.exists()) {
                zipFile.delete();
            }

            Path sourcePath = worldDir.toPath();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                    
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        String dirName = dir.getFileName().toString();
                        // On ignore le dossier entier s'il est dans la liste d'exclusion
                        if (EXCLUDED_FOLDERS.contains(dirName.toLowerCase())) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String fileName = file.getFileName().toString();
                        
                        // On ignore les fichiers spécifiques inutiles
                        if (EXCLUDED_FILES.contains(fileName.toLowerCase())) {
                            return FileVisitResult.CONTINUE;
                        }
                        
                        String zipEntryName = sourcePath.relativize(file).toString().replace("\\", "/");
                        zos.putNextEntry(new ZipEntry(zipEntryName));
                        Files.copy(file, zos);
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}