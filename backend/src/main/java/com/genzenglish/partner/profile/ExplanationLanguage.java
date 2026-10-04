package com.genzenglish.partner.profile;

/** The language Luna uses when the learner taps "Explain in my language". */
public enum ExplanationLanguage {
    ENGLISH("en", "English", "English"),
    HINDI("hi", "Hindi", "हिन्दी"),
    KANNADA("kn", "Kannada", "ಕನ್ನಡ"),
    TAMIL("ta", "Tamil", "தமிழ்"),
    TELUGU("te", "Telugu", "తెలుగు"),
    MALAYALAM("ml", "Malayalam", "മലയാളം"),
    MARATHI("mr", "Marathi", "मराठी"),
    BENGALI("bn", "Bengali", "বাংলা");

    private final String code;
    private final String englishName;
    private final String nativeName;

    ExplanationLanguage(String code, String englishName, String nativeName) {
        this.code = code;
        this.englishName = englishName;
        this.nativeName = nativeName;
    }

    public String code() {
        return code;
    }

    public String englishName() {
        return englishName;
    }

    public String nativeName() {
        return nativeName;
    }
}
