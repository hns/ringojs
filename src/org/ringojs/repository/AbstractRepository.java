/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2003 Helma Software. All Rights Reserved.
 *
 * $RCSfile: AbstractRepository.java,v $
 * $Author: hannes $
 * $Revision: 1.7 $
 * $Date: 2006/04/07 14:37:11 $
 */

package org.ringojs.repository;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides common methods and fields for the default implementations of the
 * repository interface
 */
public abstract class AbstractRepository implements Repository {

    boolean moduleRoot = false;

    /**
     * Parent repository this repository is contained in.
     */
    protected AbstractRepository parent;

    protected AbstractRepository root;

    /**
     * Cache for direct child repositories
     */
    Map<String, SoftReference<AbstractRepository>> repositories =
            new ConcurrentHashMap<String, SoftReference<AbstractRepository>>();

    /**
     * Cache for direct resources
     */
    Map<String, AbstractResource> resources = new ConcurrentHashMap<String, AbstractResource>();

    /**
     * Cached name for faster access
     */
    String path;

    /**
     * Cached short name for faster access
     */
    String name;

    /**
     * Called to create a child resource for this repository if it exists.
     * @param name the name of the child resource
     * @return the child resource, or null if no resource with the given name exists
     * @throws IOException an I/O error occurred
     */
    protected abstract Resource lookupResource(String name) throws IOException;

    /**
     * Create a new child reposiotory with the given name.
     * @param name the name
     * @return the new child repository
     * @throws IOException an I/O error occurred
     */
    protected abstract AbstractRepository createChildRepository(String name) throws IOException;

    /**
     * Add the repository's resources into the list, optionally descending into
     * nested repositories.
     * @param list the list to add the resources to
     * @param recursive whether to descend into nested repositories
     * @throws IOException an I/O related error occurred
     */
    protected abstract void getResources(List<Resource> list, boolean recursive)
            throws IOException;

    public static String normalizePath(String path) {
        return normalizePath(path, "/");
    }

    public static String normalizePath(String path, String separator) {
        boolean absolute = path.startsWith("/") || path.startsWith(separator);
        String[] elements = path.split(SEPARATOR);
        Deque<String> list = new LinkedList<String>();
        String last = null;
        for (String e : elements) {
            if ("..".equals(e)) {
                if (last == null || "..".equals(last)) {
                    list.add(e);
                    last = e;
                } else {
                    list.removeLast();
                }
            } else if (!".".equals(e) && !"".equals(e)) {
                list.add(e);
                last = e;
            }
        }
        StringBuilder sb = new StringBuilder(path.length());
        if (absolute) {
            sb.append(separator);
        }
        for (String e : list) {
            sb.append(e).append(separator);
        }
        return sb.toString();
    }

    /**
     * Get the full name that identifies this repository globally
     */
    public String getPath() {
        return path;
    }

    /**
     * Get the local name that identifies this repository locally within its
     * parent repository
     */
    public String getName() {
        return name;
    }

    /**
     * Mark this repository as root repository.
     */
    public void setModuleRoot(boolean root) {
        moduleRoot = root;
    }

    /**
     * Find out if this repository is a module root path.
     * @return true if this repository is on the module search path
     */
    public boolean isModuleRoot() {
        return moduleRoot;
    }

    public boolean isAbsolutePath(String path) {
        return path.startsWith("/");
    }

    /**
     * Get the path of this repository relative to its root repository.
     *
     * @return the repository path
     */
    public String getRelativePath() {
        if (moduleRoot) {
            return "";
        } else if (parent == null) {
            return path;
        } else {
            StringBuffer b = new StringBuffer();
            getRelativePath(b);
            return b.toString();
        }
    }

    private void getRelativePath(StringBuffer buffer) {
        if (!moduleRoot) {
            if (parent == null) {
                buffer.append(path);
            } else {
                parent.getRelativePath(buffer);
                buffer.append(name).append('/');
            }
        } 
    }

    /**
     * Utility method to get the name for the module defined by this resource.
     *
     * @return the module name according to the securable module spec
     */
    public String getModuleName() {
        return getRelativePath();
    }

    /**
     * Get a resource contained in this repository identified by the given local name.
     * If the name can't be resolved to a resource, a resource object is returned
     * for which {@link Resource exists()} returns <code>false<code>.
     */
    public synchronized Resource getResource(String subpath) throws IOException {
        int separator = findSeparator(subpath, 0);
        if (separator < 0) {
            return lookupResource(subpath);
        }
        AbstractRepository repo = this;
        if (isAbsolutePath(subpath)) {
            repo = (AbstractRepository) getRootRepository();
        }
        // FIXME this part is virtually identical to the one in getChildRepository()
        int last = 0;
        while (separator > -1 && repo != null) {
            String id = subpath.substring(last, separator);
            repo = repo.lookupRepository(id);
            last = separator + 1;
            separator = findSeparator(subpath, last);
        }
        return repo == null ? null : repo.lookupResource(subpath.substring(last));
    }

    /**
     * Get a child repository with the given name
     * @param subpath the name of the repository
     * @return the child repository
     */
    public AbstractRepository getChildRepository(String subpath) throws IOException {
        int separator = findSeparator(subpath, 0);
        if (separator < 0) {
            return lookupRepository(subpath);
        }
        // FIXME this part is virtually identical to the one in getResource()
        AbstractRepository repo = this;
        int last = 0;
        while (separator > -1 && repo != null) {
            String id = subpath.substring(last, separator);
            repo = repo.lookupRepository(id);
            last = separator + 1;
            separator = findSeparator(subpath, last);
        }
        return repo == null ? null : repo.lookupRepository(subpath.substring(last));
    }

    protected AbstractRepository lookupRepository(String name) throws IOException {
        if (".".equals(name) || "".equals(name)) {
            return this;
        } else if ("..".equals(name)) {
            return getParentRepository();
        }
        SoftReference<AbstractRepository> ref = repositories.get(name);
        AbstractRepository repo = ref == null ? null : ref.get();
        if (repo == null) {
            repo = createChildRepository(name);
            repositories.put(name, new SoftReference<AbstractRepository>(repo));
        }
        return repo;
    }

    /**
     * Get this repository's parent repository.
     */
    public AbstractRepository getParentRepository() {
        return parent;
    }

    public Resource[] getResources() throws IOException {
        return getResources(false);
    }

    public Resource[] getResources(boolean recursive) throws IOException {
        List<Resource> list = new ArrayList<Resource>();
        getResources(list, recursive);
        return list.toArray(new Resource[list.size()]);
    }

    public Resource[] getResources(String resourcePath, boolean recursive)
            throws IOException {
        Repository repository = getChildRepository(resourcePath);
        if (repository == null || !repository.exists()) {
            return new Resource[0];
        }
        return repository.getResources(recursive);
    }

    /**
     * Returns the repositories full path as string representation.
     * @see #getPath()
     */
    public String toString() {
        return getPath();
    }

    // Optimized separator lookup to avoid object creation overhead
    // of StringTokenizer and friends on critical code
    private int findSeparator(String path, int start) {
        int max = path.length();
        int numberOfSeparators = SEPARATOR.length();
        int found = -1;
        for (int i = 0; i < numberOfSeparators; i++) {
            char c = SEPARATOR.charAt(i);
            for (int j = start; j < max; j++) {
                if (path.charAt(j) == c) {
                    found = max = j;
                    break;
                }
            }
        }
        return found;
    }

}
