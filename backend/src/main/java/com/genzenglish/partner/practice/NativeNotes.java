package com.genzenglish.partner.practice;

import com.genzenglish.partner.profile.ExplanationLanguage;

/**
 * A plain "say this instead of that" line in the learner's own language, for when the model is unavailable or leaves
 * the native-language reason out. The English words stay in English, in quotes, because that's what the learner will
 * say out loud.
 */
final class NativeNotes {

    private NativeNotes() {
    }

    /** @return null for English, which has no separate native-language note */
    static String sayInstead(ExplanationLanguage language, String original, String better) {
        String template = switch (language) {
            case ENGLISH -> null;
            case HINDI -> "\"%s\" की जगह \"%s\" बोलिए। अंग्रेज़ी में यह ज़्यादा सही और स्वाभाविक लगता है।";
            case MARATHI -> "\"%s\" ऐवजी \"%s\" असं म्हणा. इंग्रजीत हे जास्त योग्य आणि नैसर्गिक वाटतं.";
            case BENGALI -> "\"%s\"-এর বদলে \"%s\" বলুন। ইংরেজিতে এটা বেশি সঠিক আর স্বাভাবিক শোনায়।";
            case TAMIL -> "\"%s\" என்பதற்குப் பதிலாக \"%s\" என்று சொல்லுங்கள். ஆங்கிலத்தில் இது இன்னும் சரியாகவும் "
                    + "இயல்பாகவும் இருக்கும்.";
            case TELUGU -> "\"%s\" బదులు \"%s\" అని చెప్పండి. ఇంగ్లీష్‌లో ఇది మరింత సరిగ్గా, సహజంగా ఉంటుంది.";
            case KANNADA -> "\"%s\" ಬದಲು \"%s\" ಎಂದು ಹೇಳಿ. ಇಂಗ್ಲಿಷ್‌ನಲ್ಲಿ ಇದು ಹೆಚ್ಚು ಸರಿಯಾಗಿ ಮತ್ತು ಸಹಜವಾಗಿ ಕೇಳಿಸುತ್ತದೆ.";
            case MALAYALAM -> "\"%s\" എന്നതിന് പകരം \"%s\" എന്ന് പറയൂ. ഇംഗ്ലീഷിൽ ഇത് കൂടുതൽ ശരിയും സ്വാഭാവികവുമാണ്.";
        };
        return template == null ? null : template.formatted(original, better);
    }
}
