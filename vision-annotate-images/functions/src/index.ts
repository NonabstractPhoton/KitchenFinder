import * as functions from "firebase-functions";
import vision from "@google-cloud/vision";

const client = new vision.ImageAnnotatorClient();

export const annotateImage = functions.https.onCall(async (data, context) => {
    if (!context.auth || context.auth.token?.firebase?.email_verified === false) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "annotateImage must be called while authenticated."
      );
    }
    try {
      return await client.annotateImage(JSON.parse(data));
    } catch (e: any) {
      throw new functions.https.HttpsError("internal", e.message, e.details);
    }
  });
