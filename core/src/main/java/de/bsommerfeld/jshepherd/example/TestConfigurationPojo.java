package de.bsommerfeld.jshepherd.example;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.CommentSection;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.PostInject;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Comment({
        "This is the TestConfigurationPojo file header.",
        "It demonstrates the jShepherd configuration capabilities."
})
public class TestConfigurationPojo extends ConfigurablePojo<TestConfigurationPojo> {

    @CommentSection({
            "String and Numeric Values",
            "Basic data types for testing."
    })
    @Key("test-string")
    @Comment({"Test string configuration comment 1.", "It can have multiple lines."})
    private String testString = "defaultValue";

    @Key("test-int")
    @Comment({"Test int configuration comment.", "Second comment."})
    private int testInt = 123;

    @CommentSection("Floating Point and Large Numbers")
    @Key("test-double")
    @Comment("Test double configuration comment.")
    private double testDouble = 123.456;

    @Key("test-long")
    @Comment("Test long configuration comment.")
    private long testLong = 1234567890L;

    @Key("test-float")
    @Comment("Test float configuration comment.")
    private float testFloat = 123.f;

    @CommentSection("Collections and Boolean")
    @Key("test-list")
    @Comment({"Test list configuration comment.", "TEST"})
    private List<String> testList = Arrays.asList("item1", "item2", "item3");

    @Key("test-boolean")
    @Comment("Test boolean configuration comment.")
    private boolean testBoolean = true;

    @Key("test-map")
    @Comment("Test map configuration with some default entries.")
    private Map<String, Object> testMap;

    public TestConfigurationPojo() {
        testMap = new LinkedHashMap<>();
        testMap.put("keyA", "valueA");
        testMap.put("keyB", 100);
        testMap.put("keyC", true);
    }

    @PostInject
    protected void postInject() {
        System.out.println("INFO: PostInject method called.");
        // Perform any post-initialization tasks here.
        // For example validate configuration options
        // Or update properties
    }

    public String getTestString() {
        return testString;
    }

    public void setTestString(String testString) {
        this.testString = testString;
    }

    public int getTestInt() {
        return testInt;
    }

    public void setTestInt(int testInt) {
        this.testInt = testInt;
    }

    public double getTestDouble() {
        return testDouble;
    }

    public void setTestDouble(double testDouble) {
        this.testDouble = testDouble;
    }

    public long getTestLong() {
        return testLong;
    }

    public void setTestLong(long testLong) {
        this.testLong = testLong;
    }

    public float getTestFloat() {
        return testFloat;
    }

    public void setTestFloat(float testFloat) {
        this.testFloat = testFloat;
    }

    public List<String> getTestList() {
        return testList;
    }

    public void setTestList(List<String> testList) {
        this.testList = testList;
    }

    public boolean isTestBoolean() {
        return testBoolean;
    }

    public void setTestBoolean(boolean testBoolean) {
        this.testBoolean = testBoolean;
    }

    public Map<String, Object> getTestMap() {
        return testMap;
    }

    public void setTestMap(Map<String, Object> testMap) {
        this.testMap = testMap;
    }
}