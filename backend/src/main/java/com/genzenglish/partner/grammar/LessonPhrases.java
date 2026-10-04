package com.genzenglish.partner.grammar;

import java.util.Map;

import com.genzenglish.partner.profile.ExplanationLanguage;

/**
 * What Luna says around a lesson (hello, ready, right, almost, done), in English and in the learner's explanation
 * language. The lesson content stays in English, since that's what the learner will say; these lines just make Luna feel
 * like a tutor who speaks their language. English words stay in English, in quotes.
 */
final class LessonPhrases {

    private LessonPhrases() {
    }

    enum Phrase {
        LEARN_HELLO,
        BOOST_HELLO,
        REVIEW_HELLO,
        MASTER_HELLO,
        PRACTICE_HELLO,
        READY,
        RIGHT,
        ALMOST,
        DONE
    }

    /** @param nativeText the same line in the learner's language, or null when that's English */
    record Line(String text, String nativeText) {
    }

    private static final Map<Phrase, String> ENGLISH = Map.of(
            Phrase.LEARN_HELLO, "Let's learn \"%s\" today!",
            Phrase.BOOST_HELLO, "Fluency booster! Let's practise \"%s\" out loud.",
            Phrase.REVIEW_HELLO, "Revision time! Let's practise \"%s\" again, with new questions.",
            Phrase.MASTER_HELLO, "Master it! One more round of \"%s\", a little harder this time.",
            Phrase.PRACTICE_HELLO, "Your turn to speak. Answer each one out loud.",
            Phrase.READY, "Ready? Let's begin!",
            Phrase.RIGHT, "Perfect! You got it right.",
            Phrase.ALMOST, "Almost! Here's a better way to say it.",
            Phrase.DONE, "That's the end of this round. Practice makes perfect!");

    private static final Map<ExplanationLanguage, Map<Phrase, String>> NATIVE = Map.of(
            ExplanationLanguage.HINDI, Map.of(
                    Phrase.LEARN_HELLO, "आज हम \"%s\" सीखेंगे!",
                    Phrase.BOOST_HELLO, "फ़्लुएंसी बूस्टर! चलिए \"%s\" बोलकर अभ्यास करें।",
                    Phrase.REVIEW_HELLO, "दोहराने का समय! चलिए \"%s\" का नए सवालों के साथ फिर से अभ्यास करें।",
                    Phrase.MASTER_HELLO, "अब इसमें माहिर बनिए! \"%s\" का एक और राउंड, इस बार थोड़ा मुश्किल।",
                    Phrase.PRACTICE_HELLO, "अब बोलने की बारी! हर सवाल का जवाब बोलकर दीजिए।",
                    Phrase.READY, "तैयार? चलिए शुरू करें!",
                    Phrase.RIGHT, "बहुत बढ़िया! आपने सही कहा।",
                    Phrase.ALMOST, "लगभग सही! इसे ऐसे बोलिए:",
                    Phrase.DONE, "यह राउंड पूरा हुआ। अभ्यास से ही परफ़ेक्ट बनते हैं!"),
            ExplanationLanguage.KANNADA, Map.of(
                    Phrase.LEARN_HELLO, "ಇಂದು ನಾವು \"%s\" ಕಲಿಯೋಣ!",
                    Phrase.BOOST_HELLO, "ಫ್ಲೂಯೆನ್ಸಿ ಬೂಸ್ಟರ್! \"%s\" ಅನ್ನು ಮಾತನಾಡಿ ಅಭ್ಯಾಸ ಮಾಡೋಣ.",
                    Phrase.REVIEW_HELLO, "ಪುನರಾವರ್ತನೆಯ ಸಮಯ! \"%s\" ಅನ್ನು ಹೊಸ ಪ್ರಶ್ನೆಗಳೊಂದಿಗೆ ಮತ್ತೊಮ್ಮೆ ಅಭ್ಯಾಸ ಮಾಡೋಣ.",
                    Phrase.MASTER_HELLO, "ಇದರಲ್ಲಿ ಪರಿಣತರಾಗಿ! \"%s\" ನ ಇನ್ನೊಂದು ಸುತ್ತು, ಈ ಬಾರಿ ಸ್ವಲ್ಪ ಕಠಿಣ.",
                    Phrase.PRACTICE_HELLO, "ಈಗ ಮಾತನಾಡುವ ಸಮಯ! ಪ್ರತಿಯೊಂದು ಪ್ರಶ್ನೆಗೆ ಜೋರಾಗಿ ಉತ್ತರಿಸಿ.",
                    Phrase.READY, "ತಯಾರಾ? ಶುರು ಮಾಡೋಣ!",
                    Phrase.RIGHT, "ಅದ್ಭುತ! ನೀವು ಸರಿಯಾಗಿ ಹೇಳಿದಿರಿ.",
                    Phrase.ALMOST, "ಬಹುತೇಕ ಸರಿ! ಇದನ್ನು ಹೀಗೆ ಹೇಳಿ:",
                    Phrase.DONE, "ಈ ಸುತ್ತು ಮುಗಿಯಿತು. ಅಭ್ಯಾಸದಿಂದಲೇ ಪರಿಪೂರ್ಣತೆ!"),
            ExplanationLanguage.TAMIL, Map.of(
                    Phrase.LEARN_HELLO, "இன்று நாம் \"%s\" கற்றுக்கொள்வோம்!",
                    Phrase.BOOST_HELLO, "ஃப்ளூயன்சி பூஸ்டர்! \"%s\"-ஐ பேசிப் பயிற்சி செய்வோம்.",
                    Phrase.REVIEW_HELLO, "மீண்டும் பயிற்சி செய்யும் நேரம்! \"%s\"-ஐ புதிய கேள்விகளுடன் மறுபடியும் பயிற்சி செய்வோம்.",
                    Phrase.MASTER_HELLO, "இதில் நிபுணராகுங்கள்! \"%s\"-இன் இன்னொரு சுற்று, இந்த முறை கொஞ்சம் கடினம்.",
                    Phrase.PRACTICE_HELLO, "இப்போது பேசும் நேரம்! ஒவ்வொரு கேள்விக்கும் சத்தமாகப் பதில் சொல்லுங்கள்.",
                    Phrase.READY, "தயாரா? தொடங்கலாம்!",
                    Phrase.RIGHT, "அருமை! நீங்கள் சரியாகச் சொன்னீர்கள்.",
                    Phrase.ALMOST, "கிட்டத்தட்ட சரி! இதை இப்படிச் சொல்லுங்கள்:",
                    Phrase.DONE, "இந்தச் சுற்று முடிந்தது. பயிற்சியே முழுமை தரும்!"),
            ExplanationLanguage.TELUGU, Map.of(
                    Phrase.LEARN_HELLO, "ఈ రోజు మనం \"%s\" నేర్చుకుందాం!",
                    Phrase.BOOST_HELLO, "ఫ్లూయెన్సీ బూస్టర్! \"%s\"ని మాట్లాడుతూ ప్రాక్టీస్ చేద్దాం.",
                    Phrase.REVIEW_HELLO, "మళ్ళీ ప్రాక్టీస్ చేసే సమయం! \"%s\"ని కొత్త ప్రశ్నలతో మళ్ళీ సాధన చేద్దాం.",
                    Phrase.MASTER_HELLO, "ఇందులో నిపుణులు అవ్వండి! \"%s\" మరో రౌండ్, ఈసారి కొంచెం కష్టంగా.",
                    Phrase.PRACTICE_HELLO, "ఇప్పుడు మాట్లాడే సమయం! ప్రతి ప్రశ్నకు గట్టిగా సమాధానం చెప్పండి.",
                    Phrase.READY, "సిద్ధమా? మొదలుపెడదాం!",
                    Phrase.RIGHT, "అద్భుతం! మీరు సరిగ్గా చెప్పారు.",
                    Phrase.ALMOST, "దాదాపు సరైనది! దీన్ని ఇలా చెప్పండి:",
                    Phrase.DONE, "ఈ రౌండ్ ముగిసింది. సాధనతోనే పరిపూర్ణత!"),
            ExplanationLanguage.MALAYALAM, Map.of(
                    Phrase.LEARN_HELLO, "ഇന്ന് നമുക്ക് \"%s\" പഠിക്കാം!",
                    Phrase.BOOST_HELLO, "ഫ്ലൂവൻസി ബൂസ്റ്റർ! \"%s\" സംസാരിച്ച് പരിശീലിക്കാം.",
                    Phrase.REVIEW_HELLO, "വീണ്ടും പരിശീലിക്കാനുള്ള സമയം! പുതിയ ചോദ്യങ്ങളുമായി \"%s\" ഒരിക്കൽ കൂടി പരിശീലിക്കാം.",
                    Phrase.MASTER_HELLO, "ഇതിൽ വിദഗ്ധരാകൂ! \"%s\"-ന്റെ ഒരു റൗണ്ട് കൂടി, ഇത്തവണ അൽപ്പം കടുപ്പം.",
                    Phrase.PRACTICE_HELLO, "ഇനി സംസാരിക്കാനുള്ള സമയം! ഓരോ ചോദ്യത്തിനും ഉറക്കെ ഉത്തരം പറയൂ.",
                    Phrase.READY, "തയ്യാറാണോ? തുടങ്ങാം!",
                    Phrase.RIGHT, "കൊള്ളാം! നിങ്ങൾ ശരിയായി പറഞ്ഞു.",
                    Phrase.ALMOST, "ഏകദേശം ശരി! ഇത് ഇങ്ങനെ പറയൂ:",
                    Phrase.DONE, "ഈ റൗണ്ട് കഴിഞ്ഞു. പരിശീലനമാണ് പൂർണ്ണതയുടെ വഴി!"),
            ExplanationLanguage.MARATHI, Map.of(
                    Phrase.LEARN_HELLO, "आज आपण \"%s\" शिकूया!",
                    Phrase.BOOST_HELLO, "फ्लुएन्सी बूस्टर! \"%s\" बोलून सराव करूया.",
                    Phrase.REVIEW_HELLO, "उजळणीची वेळ! नव्या प्रश्नांसह \"%s\" चा पुन्हा सराव करूया.",
                    Phrase.MASTER_HELLO, "यात पारंगत व्हा! \"%s\" ची आणखी एक फेरी, या वेळी थोडी अवघड.",
                    Phrase.PRACTICE_HELLO, "आता बोलण्याची वेळ! प्रत्येक प्रश्नाचं उत्तर मोठ्याने सांगा.",
                    Phrase.READY, "तयार? चला सुरू करूया!",
                    Phrase.RIGHT, "छान! तुम्ही बरोबर बोललात.",
                    Phrase.ALMOST, "जवळजवळ बरोबर! हे असं म्हणा:",
                    Phrase.DONE, "ही फेरी संपली. सरावानेच परिपूर्णता येते!"),
            ExplanationLanguage.BENGALI, Map.of(
                    Phrase.LEARN_HELLO, "আজ আমরা \"%s\" শিখব!",
                    Phrase.BOOST_HELLO, "ফ্লুয়েন্সি বুস্টার! চলুন \"%s\" বলে বলে অনুশীলন করি।",
                    Phrase.REVIEW_HELLO, "আবার অনুশীলনের সময়! চলুন নতুন প্রশ্ন দিয়ে \"%s\" আরেকবার অনুশীলন করি।",
                    Phrase.MASTER_HELLO, "এতে দক্ষ হয়ে উঠুন! \"%s\"-এর আরেকটা রাউন্ড, এবার একটু কঠিন।",
                    Phrase.PRACTICE_HELLO, "এবার বলার পালা! প্রতিটি প্রশ্নের উত্তর জোরে বলুন।",
                    Phrase.READY, "তৈরি? চলুন শুরু করি!",
                    Phrase.RIGHT, "দারুণ! আপনি ঠিক বলেছেন।",
                    Phrase.ALMOST, "প্রায় ঠিক! এটা এভাবে বলুন:",
                    Phrase.DONE, "এই রাউন্ড শেষ। অনুশীলনেই সাফল্য!"));

    /** @param title fills the "%s" in hello lines; ignored by the others */
    static Line line(Phrase phrase, ExplanationLanguage language, String title) {
        String text = ENGLISH.get(phrase).formatted(title);
        Map<Phrase, String> nativeLines = language == null ? null : NATIVE.get(language);
        return new Line(text, nativeLines == null ? null : nativeLines.get(phrase).formatted(title));
    }
}
