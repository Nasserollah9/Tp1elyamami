package ma.emsi.elyamami.tp1elyamami.Llm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.*;
import jakarta.json.stream.JsonGenerator;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
@ApplicationScoped
public class JSonUtilPourGemini implements Serializable {
    private String systemRole;
    /**
     * Le pointer cible toujours l'ajout d'un nouvel élément à la fin du tableau "contents".
     */
    private final JsonPointer pointer = Json.createPointer(("/contents/-"));

    /**
     * Requête JSON persistante : contient UNIQUEMENT l'objet racine avec le tableau "contents" (l'historique).
     * La structure "system_instruction" n'est plus stockée ici.
     */
    private JsonObject requeteJson;
    private String texteRequeteJson;

    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
    }

    @Inject
    private LlmClientPourGemini geminiClient;

    /**
     * Envoi une requête à l'API de Gemini.
     *
     * @param question question posée par l'utilisateur
     * @return la réponse de l'API.
     * @throws RequeteException exception lancée dans le cas où la requête a été rejetée par l'API.
     */
    public LlmInteraction envoyerRequete(String question) throws RequeteException {
        // 1. Mise à jour de l'historique (this.requeteJson contient désormais UNIQUEMENT le tableau "contents")
        if (this.requeteJson == null) {
            // Premier tour : requeteJson est initialisé avec le premier message utilisateur.
            this.requeteJson = creerRequeteJson(question);
        } else {
            // Tours suivants : Ajout du nouveau message utilisateur à l'historique existant.
            ajouteQuestionDansJsonRequete(question);
        }

        // 2. Construction de la requête finale (system_instruction + contents)
        String requestBody = buildFullRequestJson(this.systemRole, this.requeteJson);

        Entity<String> entity = Entity.entity(requestBody, MediaType.APPLICATION_JSON_TYPE);

        // Pour afficher la requête JSON dans la page JSF
        this.texteRequeteJson = prettyPrinting(Json.createReader(new StringReader(requestBody)).readObject());

        // 3. Envoi de la requête
        try (Response response = geminiClient.envoyerRequete(entity)) {
            // Entité incluse dans la réponse (texte au format JSON)
            String texteReponseJson = response.readEntity(String.class);
            if (response.getStatus() == 200) {
                return new LlmInteraction(this.texteRequeteJson, texteReponseJson, extractReponse(texteReponseJson));
            } else {
                // Pour voir la requête JSON s'il y a eu un problème.
                JsonObject objet = Json.createReader(new StringReader(requestBody)).readObject();
                throw new RequeteException(response.getStatus() + " : " + response.getStatusInfo(), prettyPrinting(objet));
            }
        }
    }

    /**
     * Crée l'objet JSON initial, contenant UNIQUEMENT le premier message utilisateur dans 'contents'.
     *
     * @param question question posée par l'utilisateur.
     * @return le JsonObject contenant le tableau 'contents' initial.
     */
    private JsonObject creerRequeteJson(String question) {
        // Crée l'objet qui contiendra l'historique de la conversation (le tableau 'contents')
        return Json.createObjectBuilder()
                .add("contents", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("role", "user")
                                .add("parts", Json.createArrayBuilder()
                                        .add(Json.createObjectBuilder().add("text", question)))))
                .build();
    }

    /**
     * Helper pour construire l'objet JSON complet avant l'envoi.
     * Ceci combine le rôle système statique avec l'historique de conversation dynamique.
     */
    private String buildFullRequestJson(String systemRole, JsonObject currentRequeteJson) {
        if (systemRole == null || systemRole.isEmpty()) {
            systemRole = "You are a helpful assistant.";
        }

        JsonObjectBuilder rootBuilder = Json.createObjectBuilder()
                // Ajout de l'instruction système
                .add("system_instruction", Json.createObjectBuilder()
                        .add("parts", Json.createArrayBuilder()
                                .add(Json.createObjectBuilder().add("text", systemRole))))
                // Ajout de l'historique de conversation (le tableau "contents")
                .add("contents", currentRequeteJson.getJsonArray("contents"));

        return rootBuilder.build().toString();
    }


    /**
     * Modifie le JSON de l'historique (this.requeteJson) pour ajouter la nouvelle question utilisateur.
     */
    private void ajouteQuestionDansJsonRequete(String nouvelleQuestion) {
        // Crée le nouveau JsonObject qui correspond à la nouvelle question
        JsonObject nouveauMessageJson = Json.createObjectBuilder()
                .add("text", nouvelleQuestion)
                .build();
        // Crée le JsonArray parts
        JsonObjectBuilder newPartBuilder = Json.createObjectBuilder()
                .add("role", "user")
                .add("parts", Json.createArrayBuilder()
                        .add(nouveauMessageJson)
                        .build());
        // Ajoute ce nouveau JsonObjet dans le tableau "contents" de this.requeteJson
        this.requeteJson = this.pointer.add(this.requeteJson, newPartBuilder.build());
        // Pas besoin de retourner this.requeteJson.toString() ici, il sera géré par buildFullRequestJson
    }

    /**
     * Retourne le texte formaté du document JSON pour un affichage plus agréable.
     */
    private String prettyPrinting(JsonObject jsonObject) {
        Map<String, Boolean> config = new HashMap<>();
        config.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory writerFactory = Json.createWriterFactory(config);
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = writerFactory.createWriter(stringWriter)) {
            jsonWriter.write(jsonObject);
        }
        return stringWriter.toString();
    }

    /**
     * Extrait la réponse de l'API et ajoute la réponse à l'historique (this.requeteJson) pour la prochaine requête.
     */
    private String extractReponse(String json) {
        try (JsonReader jsonReader = Json.createReader(new StringReader(json))) {
            JsonObject jsonObject = jsonReader.readObject();
            JsonObject messageReponse = jsonObject
                    .getJsonArray("candidates")
                    .getJsonObject(0)
                    .getJsonObject("content");
            // Ajoute l'objet JSON de la réponse de l'API au JSON de la prochaine requête (role: model)
            // Cible l'ajout dans le tableau "contents" de l'historique
            this.requeteJson = this.pointer.add(this.requeteJson, messageReponse);
            // Extrait seulement le texte de la réponse
            return messageReponse.getJsonArray("parts").getJsonObject(0).getString("text");
        }
    }
}
