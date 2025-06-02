package de.bsommerfeld.jshepherd.example;

import de.bsommerfeld.jshepherd.core.ConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Map;

public class AppMain {
    public static void main(String[] args) {
        Path configFile = Paths.get("test-config-complex.toml");
        Scanner scanner = new Scanner(System.in);

        System.out.println("jShepherd Configuration Manager");
        System.out.println("==============================");
        System.out.println("Using file: " + configFile.toAbsolutePath());

        // Load configuration from file (or create with defaults if it doesn't exist)
        TestConfigurationPojo config = ConfigurationLoader.load(
                configFile,
                TestConfigurationPojo::new,
                true // Enable complex save with comments
        );

        System.out.println("\nInitial configuration values:");
        printPojoState(config);

        boolean running = true;
        while (running) {
            System.out.println("\nOptions:");
            System.out.println("1. View current configuration");
            System.out.println("2. Modify string value");
            System.out.println("3. Modify integer value");
            System.out.println("4. Modify boolean value");
            System.out.println("5. Add item to list");
            System.out.println("6. Add entry to map");
            System.out.println("7. Save configuration");
            System.out.println("8. View configuration file");
            System.out.println("9. Reload configuration from file");
            System.out.println("0. Exit");
            System.out.print("\nEnter your choice: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    System.out.println("\nCurrent configuration values:");
                    printPojoState(config);
                    break;

                case "2":
                    System.out.print("Enter new string value: ");
                    String newString = scanner.nextLine();
                    config.setTestString(newString);
                    System.out.println("String value updated to: " + newString);
                    break;

                case "3":
                    System.out.print("Enter new integer value: ");
                    try {
                        int newInt = Integer.parseInt(scanner.nextLine());
                        config.setTestInt(newInt);
                        System.out.println("Integer value updated to: " + newInt);
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid number format. Please try again.");
                    }
                    break;

                case "4":
                    System.out.print("Enter new boolean value (true/false): ");
                    String boolStr = scanner.nextLine().toLowerCase();
                    if (boolStr.equals("true") || boolStr.equals("false")) {
                        boolean newBool = Boolean.parseBoolean(boolStr);
                        config.setTestBoolean(newBool);
                        System.out.println("Boolean value updated to: " + newBool);
                    } else {
                        System.out.println("Invalid boolean value. Please enter 'true' or 'false'.");
                    }
                    break;

                case "5":
                    System.out.print("Enter new list item: ");
                    String newItem = scanner.nextLine();
                    List<String> updatedList = new ArrayList<>(config.getTestList());
                    updatedList.add(newItem);
                    config.setTestList(updatedList);
                    System.out.println("Item added to list: " + newItem);
                    break;

                case "6":
                    System.out.print("Enter new map key: ");
                    String newKey = scanner.nextLine();
                    System.out.print("Enter value for key '" + newKey + "': ");
                    String newValue = scanner.nextLine();
                    config.getTestMap().put(newKey, newValue);
                    System.out.println("Added to map: " + newKey + " = " + newValue);
                    break;

                case "7":
                    System.out.println("Saving configuration...");
                    config.save();
                    System.out.println("Configuration saved to " + configFile);
                    break;

                case "8":
                    displayFileContent(configFile);
                    break;

                case "9":
                    System.out.println("Reloading configuration from file...");
                    config.reload();
                    System.out.println("Configuration reloaded.");
                    System.out.println("\nUpdated configuration values:");
                    printPojoState(config);
                    break;

                case "0":
                    System.out.println("Exiting. Any unsaved changes will be lost.");
                    running = false;
                    break;

                default:
                    System.out.println("Invalid choice. Please try again.");
                    break;
            }
        }

        scanner.close();
        System.out.println("\nApplication closed.");
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
