package de.metaphoriker.jshepherd.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class YMLSerializer {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static String toYML(Object object) {

        // Gson does problems with some characters
        if (object instanceof String) {
            return (String) object;
        }

        return GSON.toJson(object);
    }

    public static Object fromYML(String yml, Class<?> type) {

        if (type.equals(String.class)) {
            return yml; // No conversion needed for Strings
        }

        if (type.equals(Integer.class) || type.equals(int.class)) {
            return Integer.parseInt(yml);
        }

        if (type.equals(Boolean.class) || type.equals(boolean.class)) {
            return Boolean.parseBoolean(yml);
        }

        if (type.equals(Double.class) || type.equals(double.class)) {
            return Double.parseDouble(yml);
        }

        if (type.equals(Float.class) || type.equals(float.class)) {
            return Float.parseFloat(yml);
        }

        if (type.equals(Long.class) || type.equals(long.class)) {
            return Long.parseLong(yml);
        }

        if (type.equals(Short.class) || type.equals(short.class)) {
            return Short.parseShort(yml);
        }

        // Attempt to deserialize with Gson for complicated types.
        return GSON.fromJson(yml, type);
    }
}
