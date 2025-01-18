import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PackCompiler {
    private static final String BASE_COMPILED_FILE = "package packID;\r\n" + "import net.minecraftforge.fml.common.Mod;\r\n" + "@Mod(\"packID\")\r\n" + "public class ForgePackLoader {public ForgePackLoader() {}}";
    private static int killedDSStores = 0;

    public static void main(String[] args) {
        try {
            File currentDir = new File(PackCompiler.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            //Enable for IDE where path isn't where file lies.
            //currentDir = new File("D:\\MinecraftDev\\code_ocp");
            boolean onWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

            //Make sure the user has the java compiler installed.
            String result = runCommand("javac -version");
            if (!result.startsWith("javac")) {
                System.out.println(result);
                System.out.println("No Java compiler found.  Please install a Java JDK compiler.");
                return;
            } else {
                System.out.println("Found Java compipler, looking for packs.");
            }

            //Kill off any DS_Store files as they'll foul Forge.
            killDSStores(new File(currentDir, "src/main"));
            if (killedDSStores > 0) {
                System.out.println("Compile-inator Packing Systems V2.0 found " + killedDSStores + " DS_Stores in your files.  These have been sent to the shadow realm.");
            }

            //Remove any old packs.
            File libsDir = new File(currentDir, "build/libs");
            if (libsDir.exists()) {
                for (File file : libsDir.listFiles()) {
                    file.delete();
                }
            }

            //Make sure ForgePackLoader.java is proper to all included mods.
            Set<String> packIDs = new HashSet<>();
            File packAssetRootDir = new File(currentDir, "src/main/resources/assets");
            for (File file : packAssetRootDir.listFiles()) {
                if (file.isDirectory()) {
                    String packID = file.getName();
                    packIDs.add(packID);
                    File packCompiledFileDir = new File(currentDir, "src/main/java/" + packID);
                    System.out.println("Found pack with ID: " + packID);

                    File packCompiledFile = new File(packCompiledFileDir, "ForgePackLoader.java");
                    String data = BASE_COMPILED_FILE.replace("packID", packID);
                    BufferedWriter fileOutput = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(packCompiledFile)));
                    fileOutput.write(data);
                    fileOutput.close();
                }
            }

            //Check for pngs in the texture folder and make any item JSON models as required.
            for (String packID : packIDs) {
                Set<String> requiredJSONs = new HashSet<>();
                File packAssetItemJSONDir = new File(packAssetRootDir, packID + "/models/item");
                File packAssetItemPNGDir = new File(packAssetRootDir, packID + "/textures/item");
                if (packAssetItemPNGDir.exists()) {
                    for (File pngFile : packAssetItemPNGDir.listFiles()) {
                        String rawFileName = pngFile.getName().substring(0, pngFile.getName().lastIndexOf("."));
                        File jsonFile = new File(packAssetItemJSONDir, packID + "." + rawFileName + ".json");
                        requiredJSONs.add(jsonFile.getName());
                        if (!jsonFile.exists()) {
                            String createdJSON = "{\"parent\":\"mts:item/basic\",\"textures\":{\"layer0\": \"" + packID + ":item/" + rawFileName + "\"}}";
                            BufferedWriter fileOutput = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(jsonFile)));
                            fileOutput.write(createdJSON);
                            fileOutput.close();
                            System.out.println("Auto-created missing item JSON for " + packID + ":" + rawFileName);
                        }
                    }
                    for (File jsonFile : packAssetItemJSONDir.listFiles()) {
                        if (!requiredJSONs.contains(jsonFile.getName())) {
                            jsonFile.delete();
                            System.out.println("Removed un-needed JSON file " + packID + ":" + jsonFile.getName());
                        }
                    }
                }
            }

            System.out.println("All files checked, compiling.");
            result = runCommand(onWindows ? "gradlew.bat build" : "./gradlew build");

            //Re-name compiled file, we know there will only be one, but not what it's called.
            for (File file : libsDir.listFiles()) {
                file.renameTo(new File(libsDir, "Pack-1.16.5.jar"));
            }

            //Now make a 1.12.2 compliant pack by zipping everything in the assets folder.
            ZipOutputStream pack = new ZipOutputStream(new FileOutputStream(new File(libsDir, "Pack-1.12.2.jar")));
            addToZip(packAssetRootDir, pack, packAssetRootDir.getAbsolutePath().length() - "assets".length());
            pack.close();

            System.out.println("Your pack is located in " + libsDir.getAbsolutePath().toString());
            System.out.println("If you haven't already, please edit the mods.toml file, located in " + (new File(currentDir, "src/main/resources/META-INF").getAbsolutePath()));
            System.out.println("This parser cannot edit or know all the various things in that file, so you must edit it before running this script.");
        } catch (Exception e) {
            System.out.println("Build failed!");
            e.printStackTrace();
        }
    }

    private static void killDSStores(File directory) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                killDSStores(file);
            } else if (file.getName().contains("DS_Store")) {
                file.delete();
                ++killedDSStores;
            }
        }
    }

    private static String runCommand(String command) throws Exception {
        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();

            BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            BufferedReader error = new BufferedReader(new InputStreamReader(process.getErrorStream(), "UTF-8"));
            String result = "";
            while (output.ready()) {
                result += output.readLine() + "\n";
            }
            while (error.ready()) {
                result += error.readLine() + "\n";
            }
            return result;
        } catch (Exception e) {
            System.out.println("Error running command: " + command);
            throw e;
        }
    }
    
    private static void addToZip(File directory, ZipOutputStream pack, int directoryLength) throws Exception {
        for (File file : directory.listFiles()) {
            try {
                if (file.isDirectory()) {
                    addToZip(file, pack, directoryLength);
                } else {
                    //Need to replace \ with / since parser expects URI format, not Windows.
                    ZipEntry entry = new ZipEntry(file.getAbsolutePath().substring(directoryLength).replace('\\', '/'));
                    pack.putNextEntry(entry);
                    FileInputStream stream = new FileInputStream(file);
                    byte[] bytes = new byte[1024];
                    int bytesRead;
                    while((bytesRead = stream.read(bytes)) > 0) {
                        pack.write(bytes, 0, bytesRead);
                    }
                    pack.closeEntry();
                }
            }catch (Exception e) {
                System.out.println("Error zipping file: " + file);
                throw e;
            }
        }
    }
}
