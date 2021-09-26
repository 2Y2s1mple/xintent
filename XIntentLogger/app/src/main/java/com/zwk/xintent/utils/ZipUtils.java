package com.zwk.xintent.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipUtils {
    private static final String TAG = "ZipUtils";

    public static void ZipFolder(String srcFilePath, String zipFilePath) {
        try {
            ZipOutputStream outZip = new ZipOutputStream(new FileOutputStream(zipFilePath));
            File file = new File(srcFilePath);
            ZipFiles(file.getParent() + File.separator, file.getName(), outZip);
            outZip.finish();
            outZip.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void ZipFiles(String folderPath, String filePath, ZipOutputStream zipOutputSteam) {
        if (zipOutputSteam == null) return;
        try {
            File file = new File(folderPath + filePath);
            if (file.isFile()) {
                ZipEntry zipEntry = new ZipEntry(filePath);
                FileInputStream inputStream = new FileInputStream(file);
                zipOutputSteam.putNextEntry(zipEntry);
                int len;
                byte[] buffer = new byte[4096];
                while ((len = inputStream.read(buffer)) != -1) {
                    zipOutputSteam.write(buffer, 0, len);
                }
                zipOutputSteam.closeEntry();
            } else {
                String fileList[] = file.list();
                if (fileList.length <= 0) {
                    ZipEntry zipEntry = new ZipEntry(filePath + File.separator);
                    zipOutputSteam.putNextEntry(zipEntry);
                    zipOutputSteam.closeEntry();
                }
                for (int i = 0; i < fileList.length; i++) {
                    ZipFiles(folderPath, filePath + File.separator + fileList[i], zipOutputSteam);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static InputStream readOneFromZip(String zipFilePath, String filePath) {
        try {
            ZipFile zipFile = new ZipFile(zipFilePath);
            ZipEntry zipEntry = zipFile.getEntry(filePath);
            return zipFile.getInputStream(zipEntry);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
