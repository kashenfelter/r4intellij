/*
 * Copyright 2011 Holger Brandl
 *
 * This code is licensed under BSD. For details see
 * http://www.opensource.org/licenses/bsd-license.php
 */

package com.r4intellij.packages;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.DocumentUtil;
import com.r4intellij.RFileType;
import com.r4intellij.interpreter.SimpleFunctionVisitor;
import com.r4intellij.settings.RSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.r4intellij.RFileType.DOT_R_EXTENSION;
import static com.r4intellij.packages.PackageServiceUtilKt.getInstalledPackageVersions;
import static com.r4intellij.packages.RHelperUtil.PluginResourceFile;
import static com.r4intellij.packages.RHelperUtil.RRunResult;
import static com.r4intellij.settings.LibraryUtil.R_SKELETONS;
import static com.r4intellij.settings.LibraryUtil.createLibrary;


public class RSkeletonGenerator {

    public static final String SKELETON_TITLE = ".skeleton_package_title";
    public static final String SKELETON_PCKG_VERSION = ".skeleton_package_version";
    public static final String SKELETON_DEPENDS = ".skeleton_package_depends";
    public static final String SKELETON_IMPORTS = ".skeleton_package_imports";
    public static final String SKELETON_SKEL_VERSION = ".skeleton_version";


    // note: just change in sync with ./r-helpers/skeletonize_package.R
    public static final int CUR_SKELETONIZE_VERSION = 5;


    public static final Set<String> DEFAULT_PACKAGES = Sets.newHashSet("stats", "graphics", "grDevices", "utils", "datasets", "grid", "methods", "base");

    protected static final Logger LOG = Logger.getInstance("#" + RSkeletonGenerator.class.getName());

    public static final PluginResourceFile RHELPER_SKELETONIZE_PACKAGE = new PluginResourceFile("skeletonize_package.R");

    public static final String SKELETON_DIR_NAME = "r_skeletons";


    // entry point for configurable interface and action
    public static void updateSkeletons(@NotNull final Project project) {

        final Application application = ApplicationManager.getApplication();

        // first (if not yet present create a library within the project that will contain the package skeletons
        application.runWriteAction(() -> {
            // add all paths to library
            final String skeletonLibraryPath = RSkeletonGenerator.getSkeletonsPath();

            File skeletonLibDir = new File(skeletonLibraryPath);
            if (!skeletonLibDir.exists()) {
                if (!skeletonLibDir.mkdir()) {
                    LOG.warn("Failed to create skeleton dir");
                }
            }

            createLibrary(R_SKELETONS, Collections.singletonList(skeletonLibraryPath), project);
        });

        // now do the actual work
        generateSmartSkeletons(project);
    }


    private static void generateSmartSkeletons(@NotNull final Project project) {
        // http://stackoverflow.com/questions/18725340/create-a-background-task-in-intellij-plugin

        // http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Updating Skeletons", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
//                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                List<String> updatedPackages = updateSkeletons(indicator, project);

                // trigger index cache refresh

//                ApplicationManager.getApplication().invokeLater(() -> {
//                    VirtualFileManager.getInstance().syncRefresh();
//
//                    });

//                RefreshQueue.getInstance().
//                VirtualFileManager.getInstance().syncRefresh();
                VirtualFileManager.getInstance().asyncRefresh(() -> {

//                    File createdFileOutsideVFS = new File(RSkeletonGenerator.getSkeletonsPath(), "ggExtra.R");
//                    System.err.println("ggextra file exists:" + createdFileOutsideVFS.exists());
//                    System.err.println("ggextra vfs instance:" + VfsUtil.findFileByIoFile(createdFileOutsideVFS, false));

//                    VfsUtil.findFileByIoFile(new File(RSkeletonGenerator.getSkeletonsPath(), "ggExtra.R"), true); // the actual file

                    // also add those to the updated set which are not yet part of the index
                    PackageServiceUtilKt.rebuildIndex(project);
                });


                // TBD seems to cause thread exeception. Needed?
//                        PsiDocumentManager.getInstance(project).commitAllDocuments();

//                String path = RSkeletonGenerator.getSkeletonsPath();
//
//                VirtualFile skeletonDir = VfsUtil.findFileByIoFile(new File(path), true);
//
//                if (skeletonDir == null) {
//                    LOG.info("Failed to locate skeletons directory");
//                    return;
//                }
//                });
            }
        });
//        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Updating Skeletons", false) {
//            @Override
//            public void run(@NotNull ProgressIndicator indicator) {
//                File createdFileOutsideVFS = new File(RSkeletonGenerator.getSkeletonsPath(), "ggExtra.R");
//
//                // ... some logic to create this file ...
//
//                // once done wait for VFS and stub-index to pick up the changes and do some follow up task using the stub-index
//                VirtualFileManager.getInstance().asyncRefresh(() -> {
//
//                    System.err.println("ggextra file exists:" + createdFileOutsideVFS.exists());
//                    System.err.println("ggextra vfs instance:" + VfsUtil.findFileByIoFile(createdFileOutsideVFS, false));
//
////                    VfsUtil.findFileByIoFile(new File(RSkeletonGenerator.getSkeletonsPath(), "ggExtra.R"), true); // the actual file
//
//                    // also add those to the updated set which are not yet part of the index
//                    PackageServiceUtilKt.rebuildIndex(project);
//                });
//            }
//        });

    }


    public static String getSkeletonsPath() {
        final String basePath = PathManager.getSystemPath();

        // todo this should include the interpreter version as well
        String interpreterPath = RSettings.getInstance().getInterpreterPath();
        int interpreterHash = FileUtil.toSystemIndependentName(interpreterPath).hashCode();

        return (basePath + File.separator + SKELETON_DIR_NAME) + File.separator + Math.abs(interpreterHash) + File.separator;
    }


    // extracted from https://support.rstudio.com/hc/en-us/articles/201057987-Quick-list-of-useful-R-packages
    public static final List<String> COMMON_PACKAGES = Arrays.asList("RSQLite", "xlsx", "foreign", "dplyr", "tidyr",
            "stringr", "lubridate", "ggplot2", "ggvis", "rgl", "htmlwidgets", "googleVis", "car", "mgcv", "nlme",
            "randomForest", "multcomp", "vcd", "glmnet", "elastic", "survival", "caret", "shiny", "non", "xtable",
            "maptools", "maps", "ggmap", "zoo", "xts", "quantmod", "Rcpp", "table", "parallel", "XML", "jsonlite",
            "httr", "devtools", "testthat", "roxygen2");


    public static Integer getIndexPriority(String pckgName) {
        if (DEFAULT_PACKAGES.contains(pckgName)) return 2;
        if (COMMON_PACKAGES.contains(pckgName)) return 1;
        return 0;
    }


    @NotNull
    private static List<String> updateSkeletons(@NotNull ProgressIndicator indicator, Project project) {
        final String interpreter = RSettings.getInstance().getInterpreterPath();

        if (StringUtil.isEmptyOrSpaces(interpreter)) return new ArrayList<>();

        Map<String, String> packageVersions = getInstalledPackageVersions();

        cleanUpUninstalledPackages(packageVersions);

        List<String> updated = new ArrayList<>();

        // resort them so that the most popular ones are indexed first
        List<String> packageNames = new ArrayList<>(packageVersions.keySet());
        packageNames = Ordering.natural().reverse()
                .onResultOf(RSkeletonGenerator::getIndexPriority)
                .sortedCopy(packageNames);


        // additional threading here kills the computer
        ExecutorService es = Executors.newFixedThreadPool(4);
        int processed = 0;

        for (String packageName : packageNames) {
            processed++;

            final String skeletonsPath = getSkeletonsPath();
            final File skeletonsDir = new File(skeletonsPath);

            // skip if skeleton exists already and it is not outdated
            File skeletonFile = new File(skeletonsDir, packageName + DOT_R_EXTENSION);


//            RPackage rPackage = indexCache.getPackages().stream()
//                    .filter(it -> it.getName().equals(packageName))
//                    .findFirst().orElse(null);

            // skip reindexing if package version is same as cached index version
            String installedVersion = packageVersions.get(packageName);
//            boolean isCorrectCacheVersion = rPackage != null && Objects.equals(rPackage.getVersion(), installedVersion);


            if (isValidSkeleton(skeletonFile) && isSamePckgVersion(skeletonFile, installedVersion)) {
                continue;
            }


            if (!skeletonsDir.exists() && !skeletonsDir.mkdirs()) {
                LOG.error("Can't create skeleton directory " + String.valueOf(skeletonsPath));
            }

            int finalProcessed = processed;
            es.submit(() -> {
                try {
                    LOG.info("building skeleton for package '" + packageName + "'");

                    indicator.setFraction((double) finalProcessed / (packageVersions.size()));
                    indicator.setText("Indexing '" + packageName + "'");

                    // build the skeletons in tmp and move them once done so avoid incomplete file index failures
                    File tempSkeleton = Files.createTempFile("r4j_skel_" + packageName + "_", DOT_R_EXTENSION).toFile();

                    RRunResult output = RHelperUtil.runHelperWithArgs(RHELPER_SKELETONIZE_PACKAGE, packageName, tempSkeleton.getAbsolutePath());

                    if (output != null && output.getExitCode() != 0) {
                        LOG.error("Failed to generate skeleton for '" + packageName + "'. Exit code: " + output.getExitCode());
                        LOG.error(output.getStdErr());
                    } else if (isValidSkeleton(tempSkeleton)) {
                        Files.move(tempSkeleton.toPath(), skeletonFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                        updated.add(packageName);
                    } else {
                        LOG.error("Failed to generate skeleton for '" + packageName + "'");
                    }

                } catch (IOException e) {
                    LOG.error("Failed to generate skeleton for '" + packageName + "' due to io issue", e);

                }
            });
        }

        // wait until all skeletons are built
        try {
            es.shutdown();
            es.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/virtual_file.html
//        VirtualFile.refresh()
        return updated;
    }


    private static void cleanUpUninstalledPackages(Map<String, String> packageVersions) {

        File[] skeletonFiles = new File(RSkeletonGenerator.getSkeletonsPath())
                .listFiles(pathname -> pathname.getName().endsWith(RFileType.DOT_R_EXTENSION));

        List<File> noLongerInstalled = Arrays
                .stream(skeletonFiles != null ? skeletonFiles : new File[0])
                .filter(skelFile -> {
                    String skelPckgeName = skelFile.getName().replace(RFileType.DOT_R_EXTENSION, "");

                    return !packageVersions.keySet().contains(skelPckgeName);
                }).collect(Collectors.toList());


        // remove skeletons of no longer installed from index cache

        ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
            noLongerInstalled.forEach(File::delete);
        }));


//        // also remove from package index
//        List<String> noLongerInstalledNames = noLongerInstalled.stream()
//                .map(skelFile -> skelFile.getName().replace(RFileType.DOT_R_EXTENSION, ""))
//                .collect(Collectors.toList());
//
//        RIndexCache.getInstance().updateCache(noLongerInstalledNames);
    }


    public static boolean isValidSkeleton(File skeletonFile) {
        return skeletonFile.exists() && isComplete(skeletonFile) && isCurrentSkelVersion(skeletonFile);
    }


    /**
     * Scan for final EOF tag in skeleton file.
     */
    private static boolean isComplete(File skeletonFile) {
        boolean hasEOF = false;
        try {
            Scanner scanner = new Scanner(skeletonFile);

            while (scanner.hasNextLine()) {
                String curLine = scanner.nextLine().trim();
                if (!curLine.isEmpty()) hasEOF = curLine.equals("## EOF");
            }
        } catch (FileNotFoundException e) {
            return false;
        }

        return hasEOF;
    }


    public static boolean isSamePckgVersion(File skeletonFile, String installedVersion) {
        Map<String, String> skelProps = getSkeletonProperties(skeletonFile);
        String skelPckgVersion = CharMatcher.anyOf("\"").trimFrom(skelProps.getOrDefault(SKELETON_PCKG_VERSION, ""));

        return Objects.equals(skelPckgVersion, installedVersion);
    }


    private static boolean isCurrentSkelVersion(File skeletonFile) {
        Map<String, String> skelProps = getSkeletonProperties(skeletonFile);
        Integer skelVersion = Integer.valueOf(skelProps.getOrDefault(SKELETON_SKEL_VERSION, "-1"));

        return skelVersion == CUR_SKELETONIZE_VERSION;
    }


    private static Map<String, String> getSkeletonProperties(File skeletonFile) {

        Map<String, String> skelProps = new HashMap<>();

        // use basic file search because psi-read access is not allowed from here (and it's also faster)
        Integer fileVersion = -1;
        try {
            Scanner scanner = new Scanner(skeletonFile);
            while (scanner.hasNextLine()) {
                String curLine = scanner.nextLine();
                if (curLine.startsWith(".skeleton_")) {
                    String[] splitLine = curLine.split(" = ");
                    skelProps.put(splitLine[0], splitLine[1]);
                }
            }
        } catch (FileNotFoundException | ArrayIndexOutOfBoundsException ignored) {
        }

        return skelProps;
    }


    // note keep since we want to bring back the type system some day
    private static void generateTypedSkeletonForPackage(@NotNull final VirtualFile packageDir, @NotNull final Project project) {
        String packageName = packageDir.getName();
        //TODO: DELETE THIS CHECK!!! it is here only for speeding checks while developing
//        if (!packageName.equals("base") && !packageName.equals("codetools")) {
//            return;
//        }

        VirtualFile skeletonsDir = packageDir.getParent();
        try {
            VirtualFile packageFile = skeletonsDir.findOrCreateChildData(project, packageName + DOT_R_EXTENSION);
            final Document packageDocument = FileDocumentManager.getInstance().getDocument(packageFile);

            assert packageDocument != null;
            DocumentUtil.writeInRunUndoTransparentAction(new Runnable() {
                @Override
                public void run() {
                    packageDocument.deleteString(0, packageDocument.getTextLength());
                }
            });

            for (final VirtualFile file : packageDir.getChildren()) {
                LOG.info("start processing " + file.getPath());
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                assert psiFile != null;

                // todo reenable typing
                psiFile.acceptChildren(new SimpleFunctionVisitor(packageDocument, file, project, packageName));
//                psiFile.acceptChildren(new TypedFunctionVisitor(packageDocument, file, project, packageName));
            }

        } catch (IOException e) {
            LOG.error(e);
        }
    }
}