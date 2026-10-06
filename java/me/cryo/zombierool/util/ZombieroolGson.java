package me.cryo.zombierool.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class ZombieroolGson {
    public static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    public static final Gson LENIENT = new GsonBuilder().setLenient().create();
    public static final Gson PRETTY_LENIENT = new GsonBuilder().setPrettyPrinting().setLenient().create();
    public static final Gson PLAIN = new Gson();

    private ZombieroolGson() {}
}
