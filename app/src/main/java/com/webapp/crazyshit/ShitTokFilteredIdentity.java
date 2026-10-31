package com.webapp.crazyshit;

/** Identity linkage only. A name never supplies category membership. */
final class ShitTokFilteredIdentity {
    private ShitTokFilteredIdentity() { }

    static boolean strong(String verifiedName, String profileName) {
        String a = CreatorIdentity.key(verifiedName), b = CreatorIdentity.key(profileName);
        // Preserve token boundaries; prefix, substring and edit-distance matches are not evidence.
        return !a.isEmpty() && a.equals(b);
    }

    static FapelloRepository.Model uniqueFapello(OnlyHavenRepository.Creator verified,
                                                java.util.List<FapelloRepository.Model> models) {
        FapelloRepository.Model match = null;
        for (FapelloRepository.Model model : models) {
            if (model == null || !FapelloRepository.isModelUrl(model.url)
                    || !strong(verified.name, model.name)) continue;
            if (match != null && !match.url.equals(model.url)) return null;
            match = model;
        }
        return match;
    }

    static NativeContentItem uniqueBunkr(OnlyHavenRepository.Creator verified,
                                         java.util.List<NativeContentItem> albums) {
        NativeContentItem match = null;
        for (NativeContentItem album : albums) {
            // The entire gallery identity must match. A title merely mentioning the creator
            // (including multi-person compilations) does not qualify.
            if (album == null || !BunkrRepository.isAlbumUrl(album.url)
                    || !strong(verified.name, album.title)) continue;
            if (match != null && !match.url.equals(album.url)) return null;
            match = album;
        }
        return match;
    }
}
