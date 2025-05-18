package de.bsommerfeld.jshepherd.example;

import de.bsommerfeld.jshepherd.core.ConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner; // For pausing

public class AppMain {
    public static void main(String[] args) {
        Path complexSaveFile = Paths.get("test-config-complex.yaml");
        Path simpleSaveFile = Paths.get("test-config-simple.yaml");

        System.out.println("jShepherd Configuration Demo");
        System.out.println("============================");

        // ---- Test with Complex (Annotation-Driven Comments) Save ----
        System.out.println("\n--- Testing Complex Save (with comments) ---");
        System.out.println("Using file: " + complexSaveFile.toAbsolutePath());
        try {
            Files.deleteIfExists(complexSaveFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        TestConfigurationPojo complexConfig = ConfigurationLoader.load(
                complexSaveFile,
                TestConfigurationPojo.class,
                TestConfigurationPojo::new,
                true // Enable complex save with comments
        );

        System.out.println("\nInitial values (from defaults or empty file):");
        printPojoState(complexConfig);

        System.out.println("\nModifying POJO values...");
        complexConfig.setTestString("A New String Value!");
        complexConfig.setTestInt(999);
        List<String> updatedList = new ArrayList<>(complexConfig.getTestList());
        updatedList.add("AppendedItem");
        complexConfig.setTestList(updatedList);
        complexConfig.getTestMap().put("newKey", "newValueFromApp");

        System.out.println("\nSaving modified POJO (complex save)...");
        complexConfig.save();
        displayFileContent(complexSaveFile);

        // Test reload
        System.out.println("\nSimulating external file modification for reload test...");
        System.out.println("For example, manually change 'test-int' in '" + complexSaveFile.getFileName() + "' to 777 and save the file.");
        System.out.print("Press Enter to continue after manual edit (or just proceed if no edit)...");
        new Scanner(System.in).nextLine(); // Pause for manual edit

        System.out.println("\nReloading POJO...");
        complexConfig.reload();
        System.out.println("\nValues after reload:");
        printPojoState(complexConfig);

        // ---- Test with Simple Save (no detailed comments) ----
        System.out.println("\n\n--- Testing Simple Save (minimal comments) ---");
        System.out.println("Using file: " + simpleSaveFile.toAbsolutePath());
        try {
            Files.deleteIfExists(simpleSaveFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        TestConfigurationPojo simpleConfig = ConfigurationLoader.load(
                simpleSaveFile,
                TestConfigurationPojo.class,
                TestConfigurationPojo::new,
                false // Disable complex save, use simple dump
        );
        // Modify slightly to see a different output
        simpleConfig.setTestString("Simple Save Output");
        simpleConfig.setTestBoolean(false);
        System.out.println("\nSaving POJO (simple save)...");
        simpleConfig.save();
        displayFileContent(simpleSaveFile);

        System.out.println("\nDemo finished.");
    }

    private static void printPojoState(TestConfigurationPojo pojo) {
        System.out.println("  String: " + pojo.getTestString());
        System.out.println("  Int: " + pojo.getTestInt());
        System.out.println("  Double: " + pojo.getTestDouble());
        System.out.println("  Long: " + pojo.getTestLong());
        System.out.println("  Float: " + pojo.getTestFloat());
        System.out.println("  Boolean: " + pojo.isTestBoolean());
        System.out.println("  List: " + pojo.getTestList());
        System.out.println("  Map: " + pojo.getTestMap());
    }

    private static void displayFileContent(Path filePath) {
        System.out.println("\n--- File Content: " + filePath.getFileName() + " ---");
        try {
            if (Files.exists(filePath)) {
                Files.lines(filePath).forEach(System.out::println);
            } else {
                System.out.println("(File does not exist)");
            }
        } catch (IOException e) {
            System.err.println("Could not read file '" + filePath + "' to display: " + e.getMessage());
        }
        System.out.println("--- End of File Content ---");
    }
}