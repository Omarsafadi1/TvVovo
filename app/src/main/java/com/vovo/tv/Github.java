package com.vovo.tv;

/** Endereços do repositório do GitHub configurado na compilação. */
public class Github {
    static boolean configured() {
        return BuildConfig.GITHUB_REPO != null && BuildConfig.GITHUB_REPO.contains("/");
    }

    /** Ex.: https://raw.githubusercontent.com/usuario/TvVovo/main/ */
    static String rawBase() {
        if (!configured()) return null;
        return "https://raw.githubusercontent.com/" + BuildConfig.GITHUB_REPO + "/" + BuildConfig.GITHUB_BRANCH + "/";
    }

    /** Lista de arquivos de uma pasta do repositório (API pública do GitHub). */
    static String contentsApi(String folder) {
        if (!configured()) return null;
        return "https://api.github.com/repos/" + BuildConfig.GITHUB_REPO + "/contents/" + folder
                + "?ref=" + BuildConfig.GITHUB_BRANCH;
    }
}
