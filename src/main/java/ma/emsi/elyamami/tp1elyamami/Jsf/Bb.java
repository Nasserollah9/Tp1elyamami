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

    private String question;
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

    // ========================= LOGIQUE D'ENVOI =========================

    public String envoyer() {
        if (question == null || question.isBlank()) {
            FacesMessage message = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Texte question vide",
                    "Il manque le texte de la question"
            );
            facesContext.addMessage(null, message);
            return null;
        }

        if (this.conversation.isEmpty()) {
            this.reponse = "Rôle système : " + roleSysteme.toUpperCase(Locale.FRENCH) + "\n";
            this.roleSystemeChangeable = false;
        }

        // 🔽 Appel réel au LLM via JsonUtil
        try {
            LlmInteraction interaction = jsonUtil.envoyerRequete(question);
            // On stocke uniquement le texte de la réponse
            this.reponse += "Réponse : " + interaction.reponseExtraite() + "\n";
            // Pour debug, si besoin
            this.texteRequeteJson = interaction.questionJson();
            this.texteReponseJson = interaction.texteReponseJson();
        } catch (Exception e) {
            FacesMessage message =
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Problème de connexion avec l'API du LLM",
                            "Problème de connexion avec l'API du LLM : " + e.getMessage());
            facesContext.addMessage(null, message);
            return null;
        }

        afficherConversation();
        return null;
    }

    // ========================= NOUVEAU CHAT =========================

    public String nouveauChat() {
        return "index";
    }

    // ========================= AFFICHAGE =========================

    private void afficherConversation() {
        this.conversation
                .append("== User:\n").append(question)
                .append("\n== Serveur:\n").append(reponse)
                .append("\n==========================\n");
    }

    // ========================= ROLES SYSTÈME =========================

    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            this.listeRolesSysteme = new ArrayList<>();

            String role = """
                    You are a helpful assistant. You help the user to find the information they need.
                    If the user type a question, you answer it.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Assistant"));

            role = """
                    You are an interpreter. You translate from English to French and from French to English.
                    If the user type a French text, you translate it into English.
                    If the user type an English text, you translate it into French.
                    If the text contains only one to three words, give some examples of usage of these words in English.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Traducteur Anglais-Français"));

            role = """
                    You are a travel guide. If the user type the name of a country or of a town,
                    you tell them what are the main places to visit in the country or the town
                    and you tell them the average price of a meal.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Guide touristique"));
        }

        return this.listeRolesSysteme;
    }
}
