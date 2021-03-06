package io.rj93.sarcasm.server;

import java.io.IOException;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import io.rj93.sarcasm.cnn.Prediction;
import io.rj93.sarcasm.cnn.TextCNN;
import io.rj93.sarcasm.utils.DataHelper;
import play.mvc.Http.Status;

@Path("api/")
public class Endpoints {

private static Logger logger = LogManager.getLogger(Endpoints.class);
	
	private static final TextCNN cnn = loadTextCNN();
	
	private static TextCNN loadTextCNN(){
		TextCNN cnn = null;
		try {
			logger.info("Loading CNN...");
			cnn = TextCNN.loadFromDir(DataHelper.MODELS_DIR, "model.bin");
			logger.info("Loading CNN complete");
		} catch (IOException e) {
			logger.catching(e);
			logger.fatal("CNN wasn't able to initialise");
		}
		return cnn;
	}
	
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("predict/")
	public Response predict(@FormParam("sentence") String sentence){
		logger.info("Predict: " + sentence);
		
		Response response = null;
		
		if (cnn != null){
			try {
				Prediction p = cnn.predict(sentence);
				
				JSONObject json = new JSONObject();
				json.put("sarcastic", p.isPositive());
				json.put("probabilityPositive", p.getProbabilityPositive());
				json.put("probabilityNegative", p.getProbabilityNegative());

				response = Response.ok(json.toString(), MediaType.APPLICATION_JSON).build();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			logger.fatal("CNN is null");
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity("The CNN hasn't been initialised").build();
		}
		
		return response;
	}
}
