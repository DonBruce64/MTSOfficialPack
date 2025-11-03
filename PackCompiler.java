import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PackCompiler {
    private static final String BASE_COMPILED_FILE = "package packID;\r\n" + "import net.minecraftforge.fml.common.Mod;\r\n" + "@Mod(\"packID\")\r\n" + "public class ForgePackLoader {public ForgePackLoader() {}}";
    private static int killedDSStores = 0;

    public static void main(String[] args) {
        try {
            // Parse mode flags
            boolean build116 = true;  // Build Forge 1.16.5 jar via Gradle
            boolean build112 = true;  // Build legacy assets-only jar for 1.12.2
            if (args != null && args.length > 0) {
                String mode = String.join(" ", args).toLowerCase();
                // If a specific target is requested, disable the other
                if (mode.contains("116")) {
                    build116 = true;
                    build112 = false;
                } else if (mode.contains("112")) {
                    build116 = false;
                    build112 = true;
                }
            }

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

            //Remove any old packs only if we're about to build the 1.16.5 jar
            //so that a subsequent 1.12.2-only run doesn't delete prior outputs.
            File libsDir = new File(currentDir, "build/libs");
            if (build116 && libsDir.exists()) {
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
                    File packCompiledFileDir = new File(currentDir, "src/main/java/" + packID);
                    File packCompiledFile = new File(packCompiledFileDir, "ForgePackLoader.java");
                    if (packCompiledFile.exists()) {
                        packIDs.add(packID);
                        System.out.println("Found pack with ID: " + packID);
                        String data = BASE_COMPILED_FILE.replace("packID", packID);
                        BufferedWriter fileOutput = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(packCompiledFile)));
                        fileOutput.write(data);
                        fileOutput.close();
                    }
                }
            }

            //Check for pngs in the texture folder and make any item JSON models as required.
            for (String packID : packIDs) {
                Set<String> requiredJSONs = new HashSet<>();
                File packAssetItemJSONDir = new File(packAssetRootDir, "mts/models/item");
                File packAssetItemPNGDir = new File(packAssetRootDir, packID + "/textures/item");
                if (packAssetItemPNGDir.exists()) {
                    for (File pngFile : packAssetItemPNGDir.listFiles()) {
                        String rawFileName = pngFile.getName().substring(0, pngFile.getName().lastIndexOf("."));
                        File jsonFile = new File(packAssetItemJSONDir, packID + "." + rawFileName + ".json");
                        requiredJSONs.add(jsonFile.getName());
                        if (!jsonFile.exists()) {
                            jsonFile.getParentFile().mkdirs();
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

            // Determine naming components
            String packVersion = readGradleString(new File(currentDir, "build.gradle"), "version");
            if (packVersion == null || packVersion.isEmpty()) {
                packVersion = "unknown";
            }
            String baseName = readGradleString(new File(currentDir, "build.gradle"), "archivesBaseName");
            if (baseName == null || baseName.isEmpty()) {
                baseName = "MTS Official Pack";
            }

            if (build116) {
                System.out.println("All files checked, compiling 1.16.5.");
                result = runCommand(onWindows ? "gradlew.bat build" : "./gradlew build");

                //Re-name compiled file, we know there will only be one, but not what it's called.
                if (libsDir.exists()) {
                    File[] files = libsDir.listFiles();
                    if (files != null) {
                        File target = null;
                        for (File file : files) {
                            String name = file.getName().toLowerCase();
                            if (name.endsWith(".jar") && !name.contains("-sources")) {
                                target = file;
                                break;
                            }
                        }
                        if (target != null) {
                            File newName = new File(libsDir, baseName + "-1.16.5-" + packVersion + ".jar");
                            target.renameTo(newName);
                            System.out.println("Named 1.16.5 jar: " + newName.getName());
                        }
                    }
                }
            }

            if (build112) {
                System.out.println("Creating 1.12.2 assets pack jar.");
                if (!libsDir.exists()) {
                    libsDir.mkdirs();
                }
                ZipOutputStream pack = new ZipOutputStream(new FileOutputStream(new File(libsDir, baseName + "-1.12.2-" + packVersion + ".jar")));
                addToZip(packAssetRootDir, pack, packAssetRootDir.getAbsolutePath().length() - "assets".length());
                pack.close();
            }

            System.out.println("Your pack is located in " + libsDir.getAbsolutePath().toString());
            System.out.println("If you haven't already, please edit the mods.toml file, located in " + (new File(currentDir, "src/main/resources/META-INF").getAbsolutePath()));
            System.out.println("This parser cannot edit or know all the various things in that file, so you must edit it before running this script.");
        } catch (Exception e) {
            System.out.println("Build failed!");
            e.printStackTrace();
        }
    }

    private static String readGradleString(File buildGradle, String key) {
        try {
            if (!buildGradle.exists()) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(buildGradle), "UTF-8"));
            String line;
            Pattern p = Pattern.compile("^" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]*)\"");
            while ((line = reader.readLine()) != null) {
                Matcher m = p.matcher(line.trim());
                if (m.find()) {
                    reader.close();
                    return m.group(1);
                }
            }
            reader.close();
        } catch (Exception ignored) {}
        return null;
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
