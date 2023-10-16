import * as functions from "firebase-functions";
// import { StringParam } from "firebase-functions/lib/params/types";

const { Configuration, OpenAIApi } = require("openai");
const settings = {
  secrets: ["OPENAI_API_KEY"]
};

export const generateParagraphText = functions.runWith(settings).https.onCall(async (data, context) => {

    const configuration = new Configuration({
      apiKey: process.env.OPENAI_API_KEY
    });
    const openai = new OpenAIApi(configuration);

    if (configuration.apiKey == null || !context.auth || context.auth?.token?.firebase?.email_verified === false) {
        throw new functions.https.HttpsError(
          "unauthenticated",
          "generateParagraphText requires authentication and a properly stored API key in order to not be misused"
        );
      }

      function generatePrompt(labels:Array<any>)
      {
        let str = "Using the following labels and associated accuracy scores, identify what ingredient is being describing, then return an informational paragraph on how to cook with that ingredient of at least 4 sentences. ";
        labels.forEach((element:any) => {
          str = str.concat("Label: "+element[0]+", Score: "+element[1]+". ");
        });
        str = str.concat("Omit text before the paragraph, as this response will be shown to clients");
        return str;
      }

      let descriptions = new Array<any>();

      let dataArray:Array<JSON> = JSON.parse(data);
      dataArray.forEach((element: any) => {
          descriptions.push([element.description, element.score]);
      });

      try {
        const completion = await openai.createCompletion({
          model: "text-curie-001",
          prompt: generatePrompt(descriptions),
          temperature: 0,
          max_tokens: 200,
        });
        return completion.data.choices[0].text;
      } 
      catch(e:any) {
        throw new functions.https.HttpsError("internal", e.message, e.details);
      }
});

