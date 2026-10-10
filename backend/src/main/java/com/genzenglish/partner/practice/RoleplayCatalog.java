package com.genzenglish.partner.practice;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.genzenglish.partner.practice.SuccessCriteria.Criterion;

/**
 * Every roleplay scene the learning path can point to (PathCatalog refers to these by slug). Luna plays one side; the
 * learner has a concrete goal and success criteria, so the scene has a shape, an ending and a scorecard.
 */
public final class RoleplayCatalog {

    private RoleplayCatalog() {
    }

    /** How demanding a scene is: everyday small talk up to formal interviews with follow-up questions. */
    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    /**
     * @param kaiRole     who Luna plays, written to follow "You play"
     * @param learnerRole who the learner plays, written to follow "the learner plays"
     * @param opening     Luna's first line, in character
     * @param targetTurns learner answers before Luna wraps the scene up
     * @param criteria    what the learner has to do for the scene to count as a success; shown before they start and
     *                    scored at the end
     */
    public record Roleplay(String slug, String title, Difficulty difficulty, String kaiRole, String learnerRole,
            String goal, String opening, int targetTurns, List<Criterion> criteria) {

        public Roleplay {
            criteria = List.copyOf(criteria);
        }
    }

    // Cue words for the offline scorecard. The learner's words arrive lower-cased with punctuation stripped.
    private static final String DAYS = "today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday"
            + "|sunday|the weekend|this week|evening|morning";

    static final List<Roleplay> ALL = List.of(
            new Roleplay("ordering-at-cafe", "Ordering at a café", Difficulty.EASY,
                    "a cheerful barista at a busy café near a college",
                    "a customer ordering for themselves and a friend",
                    "Order two drinks and something to eat, ask one question about the menu, and pay.",
                    "Hi, welcome in! What can I get started for you today?", 8,
                    List.of(Criterion.of("Order your drinks and something to eat",
                                    "coffee|chai|tea|latte|cappuccino|mocha|espresso|juice|shake|milkshake|lassi"
                                            + "|smoothie|lemonade|cold drink|sandwich|burger|croissant|cake|muffin"
                                            + "|pastry|brownie|fries|puff|samosa|cookie|wrap|pizza|toast|maggi"
                                            + "|snack|something to eat"),
                            Criterion.of("Ask a question about the menu",
                                    "what is|what's|which|is there|are there|do you have|does it|does the|is it"
                                            + "|how much|can you tell|what comes|recommend|suggest|any offers"),
                            Criterion.of("Pay and wrap up the order",
                                    "pay|paying|card|upi|cash|gpay|google pay|phonepe|paytm|bill|total"
                                            + "|that's all|that's it"))),
            new Roleplay("tell-me-about-yourself", "Tell me about yourself", Difficulty.MEDIUM,
                    "an interviewer at a mid-size software company, starting a first-round interview",
                    "a final-year student or fresher interviewing for an entry-level role",
                    "Introduce yourself clearly: who you are now, what you've done, and what you want next.",
                    "Thanks for joining. Let's start simple: tell me a little about yourself.", 10,
                    List.of(Criterion.of("Say who you are now",
                                    "i'm|i am|my name|myself|student|final year|fresher|graduate|graduated"
                                            + "|studying|working as"),
                            Criterion.of("Mention something you've done",
                                    "project|internship|intern|built|worked|led|organised|organized|won|completed"
                                            + "|developed|volunteered|club"),
                            Criterion.of("Say what you want next",
                                    "want to|looking for|hope to|goal|aim|would like|i'd like|interested in"
                                            + "|plan to|excited to|next step|grow"))),
            new Roleplay("tech-interview-intro", "Tech interview intro", Difficulty.HARD,
                    "a senior engineer interviewing a candidate about their projects",
                    "a candidate walking through a project they built",
                    "Explain one project: the problem, what you built, your part in it, and what you learnt.",
                    "I see a few projects on your résumé. Pick the one you're proudest of and walk me through it.", 10,
                    List.of(Criterion.of("Explain the problem your project solves",
                                    "problem|solve|solves|solved|issue|needed|users|manual|difficult|hard to"
                                            + "|struggle|pain"),
                            Criterion.of("Say what you built and what your part was",
                                    "i built|i developed|i made|i designed|i wrote|i handled|i was handling"
                                            + "|i was responsible|my role|my part|i worked on|i implemented"
                                            + "|i created|i led"),
                            Criterion.of("Share what you learnt",
                                    "learnt|learned|realised|realized|taught me|next time|differently|improve"
                                            + "|lesson|understood"))),
            new Roleplay("hr-interview", "HR interview", Difficulty.HARD,
                    "an HR manager running the final interview round",
                    "a candidate answering the usual HR questions",
                    "Answer questions about yourself, your strengths and why you want the job, calmly and honestly.",
                    "Good to meet you. Before we talk about the role, why do you want to work with us?", 10,
                    List.of(Criterion.of("Say why you want this job",
                                    "because|want to work|interested|excited|company|role|growth|grow|learn"
                                            + "|culture|products?|mission"),
                            Criterion.of("Back up a strength with an example",
                                    "for example|for instance|once|when i|last year|in my project|in college"
                                            + "|during my|i managed|i handled"),
                            Criterion.of("Talk about a weakness honestly and positively",
                                    "weakness|working on|improve|improving|learning to|getting better"
                                            + "|struggle with|not good at"))),
            new Roleplay("group-discussion", "Group discussion", Difficulty.HARD,
                    "another participant in a placement group discussion who has strong opinions",
                    "a participant who wants to make a clear point and respond to others",
                    "Give your view on the topic, respond politely to a different opinion, and sum up.",
                    "Okay, the topic is: should college attendance be optional? I'll start. I think it should be, "
                            + "because adults can manage their own time. What's your take?", 10,
                    List.of(Criterion.of("State your view clearly",
                                    "i think|i believe|in my opinion|i feel|my view|i agree|i disagree"
                                            + "|i strongly|my point"),
                            Criterion.of("Respond politely to a different opinion",
                                    "i see your point|that's true|i understand|fair point|you're right"
                                            + "|i agree that|good point|i respect|however|on the other hand"),
                            Criterion.of("Sum up at the end",
                                    "to sum up|in conclusion|overall|to conclude|finally|in short|so in the end"
                                            + "|to summarise|to summarize"))),
            new Roleplay("first-day-at-office", "First day at the office", Difficulty.EASY,
                    "a friendly teammate meeting a new joiner on their first day",
                    "a new joiner on their first day at work",
                    "Introduce yourself, make a bit of small talk, and find out who to ask for help.",
                    "Hey, you must be the new joiner! I sit right next to you. How's the first day going so far?", 8,
                    List.of(Criterion.of("Introduce yourself",
                                    "i'm|i am|my name|myself|i joined|i've joined|new joiner"),
                            Criterion.of("Make a bit of small talk",
                                    "weekend|lunch|commute|traffic|metro|where are you from|how long|coffee|chai"
                                            + "|city|nice to meet|how about you|what about you"),
                            Criterion.of("Find out who to ask for help",
                                    "who should i|who do i|whom|who can i|ask for help|contact|reach out"
                                            + "|who handles|who is the|point of contact"))),
            new Roleplay("pitch-project-idea", "Pitching a project idea", Difficulty.MEDIUM,
                    "a professor judging student project proposals",
                    "a student pitching their project idea",
                    "Explain the problem, your solution and why it matters, then answer the professor's questions.",
                    "Alright, you have two minutes. What's your project, and what problem does it solve?", 8,
                    List.of(Criterion.of("Explain the problem",
                                    "problem|issue|struggle|struggling|hard|difficult|waste|wasting|nobody"
                                            + "|no one|can't find|takes too long"),
                            Criterion.of("Describe your solution",
                                    "app|platform|system|solution|we will|we'll|build|tool|website|portal"
                                            + "|chatbot|dashboard"),
                            Criterion.of("Say why it matters",
                                    "impact|help|helps|save|saves|matters|important|benefit|useful"))),
            new Roleplay("college-viva", "College viva", Difficulty.HARD,
                    "an external examiner at a final-year project viva",
                    "a student defending their project",
                    "Explain your project choices clearly, and handle a question you're not sure about.",
                    "Good afternoon. Before we go into details, can you summarise your project in a few sentences?",
                    8,
                    List.of(Criterion.of("Summarise the project clearly",
                                    "our project|my project|the project|we built|we developed|we made|it is a"
                                            + "|it's a|aims to|system"),
                            Criterion.of("Explain why you made a choice",
                                    "because|chose|we used|reason|so that|instead of"),
                            Criterion.of("Handle a question you're not sure about",
                                    "not sure|don't know|i would check|good question|let me think"
                                            + "|not certain|i'll find out|i will find out|i'd have to check"))),
            new Roleplay("college-fest-friends", "Making friends at a college fest", Difficulty.EASY,
                    "a friendly student from another college, standing in a queue at a fest",
                    "a student at the fest who's open to making a new friend",
                    "Start a chat, find something you have in common, and suggest catching an event together.",
                    "This queue is moving so slowly! Are you here for the music night too?", 8,
                    List.of(Criterion.of("Start the chat in a friendly way",
                                    "yes|yeah|yep|hi|hello|hey|i am|i'm|totally"),
                            Criterion.of("Find something you have in common",
                                    "me too|same|i also|i love|i like|favourite|favorite|we both"),
                            Criterion.of("Suggest catching an event together",
                                    "let's|shall we|want to|wanna|together|join|come with|should we|we could"
                                            + "|do you want"))),
            new Roleplay("customer-care-call", "Customer care call", Difficulty.MEDIUM,
                    "a customer care agent for an online shopping app",
                    "a customer whose order arrived damaged",
                    "Explain the problem clearly, give your order details, and agree on a refund or replacement.",
                    "Thank you for calling. My name is Luna. How can I help you today?", 8,
                    List.of(Criterion.of("Explain the problem clearly",
                                    "damaged|broken|cracked|wrong|problem|issue|not working|scratched|torn"
                                            + "|missing|dented"),
                            Criterion.of("Give your order details",
                                    "order number|order id|ordered|placed|delivered|\\d{3,}"),
                            Criterion.of("Agree on a refund or replacement",
                                    "refund|replacement|replace|return|exchange|money back"))),
            new Roleplay("calling-landlord", "Calling your landlord", Difficulty.MEDIUM,
                    "a busy but reasonable landlord who gets a call from a tenant",
                    "a tenant whose water heater has stopped working",
                    "Describe the problem, ask for it to be fixed by a specific day, and confirm the plan.",
                    "Hello? Yes, who's this?", 8,
                    List.of(Criterion.of("Describe the problem",
                                    "water heater|geyser|heater|hot water|not working|stopped|broken|repair"),
                            Criterion.of("Ask for it to be fixed by a specific day",
                                    "by (?:" + DAYS + ")|(?:" + DAYS + ") (?:itself|please|if possible)"
                                            + "|as soon as possible|asap|within"),
                            Criterion.of("Confirm the plan before you hang up",
                                    "so you'll|so you will|confirm|see you|that works|sounds good|okay then"
                                            + "|ok then|thank you|thanks"))),
            new Roleplay("ask-manager-for-leave", "Asking your manager for leave", Difficulty.MEDIUM,
                    "a team manager who's a little worried about an upcoming deadline",
                    "an employee who needs three days off for a family function",
                    "Ask for leave politely, explain why, and offer a plan for your work while you're away.",
                    "Hi, you wanted to talk? I've got a few minutes before my next call.", 8,
                    List.of(Criterion.of("Ask for leave politely",
                                    "could i|can i|may i|would it be possible|i'd like to|i would like to"
                                            + "|request|please|i need to take"),
                            Criterion.of("Explain why you need it",
                                    "family|function|wedding|ceremony|marriage|cousin|sister|brother|pooja|puja"),
                            Criterion.of("Offer a plan for your work while you're away",
                                    "finish|complete|hand over|handover|cover|before i leave|colleague|update"
                                            + "|available|on call|deadline"))));

    private static final Map<String, Roleplay> BY_SLUG = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(Roleplay::slug, Function.identity()));

    public static List<Roleplay> all() {
        return ALL;
    }

    public static Optional<Roleplay> find(String slug) {
        return slug == null ? Optional.empty() : Optional.ofNullable(BY_SLUG.get(slug));
    }
}
