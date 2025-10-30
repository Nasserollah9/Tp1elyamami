package ma.emsi.elyamami.tp1elyamami.Jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import ma.emsi.elyamami.tp1elyamami.Llm.JSonUtilPourGemini;

import ma.emsi.elyamami.tp1elyamami.Llm.LlmInteraction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Named
@ViewScoped
public class Bb implements Serializable {

    private String roleSysteme;
    private boolean Debug;
    private boolean roleSystemeChangeable = true;
    private List<SelectItem> listeRolesSysteme;

    // Flag to indicate that the system role has been defined and displayed once
    private boolean roleSystemeSet = false;

    private String question;
    // Holds the latest text response from the model
    private String reponse;
    private StringBuilder conversation = new StringBuilder();

    private String texteRequeteJson;
    private String texteReponseJson;

    @Inject
    private FacesContext facesContext;

    @Inject
    private JSonUtilPourGemini jsonUtil;


    public Bb() {}

    // ========================= GETTERS / SETTERS =========================

    public String getRoleSysteme() { return roleSysteme; }
    public void setRoleSysteme(String roleSysteme) { this.roleSysteme = roleSysteme; }

    public boolean isRoleSystemeChangeable() { return roleSystemeChangeable; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getReponse() { return reponse; }
    public void setReponse(String reponse) { this.reponse = reponse; }

    public String getConversation() { return conversation.toString(); }
    public void setConversation(String conversation) { this.conversation = new StringBuilder(conversation); }

    public boolean isDebug() { return Debug; }
    public void setDebug(boolean debug) { Debug = debug; }

    public String getTexteRequeteJson() { return texteRequeteJson; }
    public String getTexteReponseJson() { return texteReponseJson; }

    public void toggleDebug() { this.setDebug(!isDebug()); }

    // ========================= APPLICATION LOGIC =========================

    public String envoyer() {
        if (question == null || question.isBlank()) {
            FacesMessage message = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Empty question text",
                    "The question text is missing"
            );
            facesContext.addMessage(null, message);
            return null;
        }

        // 1. Initialize the system role once
        if (!roleSystemeSet) {
            jsonUtil.setSystemRole(roleSysteme);
            this.roleSystemeSet = true;
            this.roleSystemeChangeable = false;
        }

        // 2. Actual LLM call
        try {
            LlmInteraction interaction = jsonUtil.envoyerRequete(question);
            // Store ONLY the raw response text
            this.reponse = interaction.reponseExtraite();

            // For debugging
            this.texteRequeteJson = interaction.questionJson();
            this.texteReponseJson = interaction.texteReponseJson();
        } catch (Exception e) {
            FacesMessage message =
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Problem connecting with the LLM API",
                            "Problem connecting with the LLM API: " + e.getMessage());
            facesContext.addMessage(null, message);
            return null;
        }

        // 3. Display and history update
        afficherConversation();
        return null;
    }

    // ========================= NEW CHAT / RESET STATE (CORRECTION) =========================

    public String nouveauChat() {
        // CORRECTION: Reset explicit de l'état du bean pour garantir l'effacement
        this.conversation = new StringBuilder();
        this.reponse = null;
        this.question = null;
        this.roleSystemeSet = false;
        this.roleSystemeChangeable = true;
        this.texteRequeteJson = null;
        this.texteReponseJson = null;

        // Note: L'instance de jsonUtil est ApplicationScoped.
        // Pour un reset complet de la conversation, jsonUtil.resetHistory() est nécessaire,
        // si cette méthode existe et met requeteJson à null.

        // Returning null allows an AJAX update to clear the conversation display on the same view.
        return null;
    }

    // ========================= DISPLAY LOGIC =========================

    private void afficherConversation() {
        // DISPLAY the System Role only once at the very beginning
        if (this.conversation.isEmpty() && this.roleSystemeSet) {
            this.conversation
                    .append("--- Applied System Role ---\n")
                    .append(this.roleSysteme)
                    .append("\n-----------------------------\n");
        }

        // DISPLAY the conversation exchange
        this.conversation
                .append("== User:\n").append(question)
                .append("\n== Serveur:\n").append(reponse)
                .append("\n==========================\n");

        // Clear question and response after adding to history

    }

    // ========================= SYSTEM ROLES DEFINITION =========================

    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            this.listeRolesSysteme = new ArrayList<>();

            // --- Standard Assistant Role ---
            String role = """
                    You are a helpful assistant. You help the user to find the information they need.
                    If the user type a question, you answer it clearly and helpfully.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Assistant"));

            // --- Translator Role ---
            role = """
                    You are an interpreter. You translate from English to French and from French to English.
                    If the user types a French text, you translate it into English.
                    If the user types an English text, you translate it into French.
                    If the text contains only one to three words, give some examples of usage of these words in English.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "English-French Translator"));

            // --- Tour Guide Role ---
            role = """
                    You are a travel guide. If the user types the name of a country or of a town,
                    you tell them what are the main places to visit in the country or the town,
                    and you tell them the average price of a meal.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Tour Guide"));

            // --- 🕷️ Pessimistic/Negative Role ---
            role = """
                    YOU ARE A PESSIMISTIC AI.
                    YOU ALWAYS ANSWER IN A NEGATIVE, SARCASTIC, OR OPPOSITE WAY TO WHAT THE USER ASKS.
                    IF THE USER SAYS SOMETHING POSITIVE, YOU RESPOND WITH A NEGATIVE OR DISCOURAGING COMMENT.
                    IF THE USER ASKS A QUESTION, YOU GIVE A PESSIMISTIC ANSWER THAT REFLECTS DOUBT OR FAILURE.
                    KEEP THE TONE POLITE BUT HOPELESS.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Pessimistic / Negative AI"));
        }

        return this.listeRolesSysteme;
    }
}
