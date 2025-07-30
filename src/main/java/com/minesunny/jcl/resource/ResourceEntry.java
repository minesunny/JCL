package com.minesunny.jcl.resource;

/**
 * @Author: Mine
 * @Email: thirteenthree@outlook.com
 * @Date: 2022-12-29 17:38
 * @Project: Lux
 */
public class ResourceEntry {
    /**
     * The "last modified" time of the origin file at the time this resource
     * was loaded, in milliseconds since the epoch.
     */
    public long lastModified = -1;


    /**
     * Loaded class.
     */
    public volatile Class<?> loadedClass = null;
}
