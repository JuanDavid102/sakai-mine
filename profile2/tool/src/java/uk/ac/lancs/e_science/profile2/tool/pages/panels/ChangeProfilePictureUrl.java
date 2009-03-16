package uk.ac.lancs.e_science.profile2.tool.pages.panels;

import org.apache.log4j.Logger;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.validation.validator.UrlValidator;

import uk.ac.lancs.e_science.profile2.api.Profile;
import uk.ac.lancs.e_science.profile2.api.ProfileImageManager;
import uk.ac.lancs.e_science.profile2.api.SakaiProxy;
import uk.ac.lancs.e_science.profile2.hbm.ProfileImageExternal;
import uk.ac.lancs.e_science.profile2.tool.ProfileApplication;
import uk.ac.lancs.e_science.profile2.tool.components.CloseButton;
import uk.ac.lancs.e_science.profile2.tool.models.SimpleText;

public class ChangeProfilePictureUrl extends Panel{
    
	private static final long serialVersionUID = 1L;
    private transient SakaiProxy sakaiProxy;
    private transient Profile profile;
	private transient Logger log = Logger.getLogger(ChangeProfilePictureUpload.class);

	public ChangeProfilePictureUrl(String id) {  
        super(id);  
        
		//get SakaiProxy API
		sakaiProxy = ProfileApplication.get().getSakaiProxy();
		
		//get Profile API
		profile = ProfileApplication.get().getProfile();
			
		//get userId
		final String userId = sakaiProxy.getCurrentUserId();
		
		//setup SimpleText object 
		SimpleText simpleText = new SimpleText();
		
		//do they already have a URL that should be loaded in here?
		String externalUrl = profile.getExternalImageUrl(userId, ProfileImageManager.PROFILE_IMAGE_MAIN);
		
		if(externalUrl != null) {
			simpleText.setText(externalUrl);
		}
		
		//setup form model using the SimpleText object
		CompoundPropertyModel formModel = new CompoundPropertyModel(simpleText);
		
        //setup form	
		Form form = new Form("form", formModel);
		form.setOutputMarkupId(true);
        
        //close button component
        CloseButton closeButton = new CloseButton("closeButton", this);
        closeButton.setOutputMarkupId(true);
		form.add(closeButton);
      
        //text
		Label textEnterUrl = new Label("textEnterUrl", new ResourceModel("text.image.url"));
		form.add(textEnterUrl);
		
		//upload
		TextField urlField = new TextField("urlField", new PropertyModel(simpleText, "text"));
		urlField.setRequired(true);
		urlField.add(new UrlValidator());
		form.add(urlField);
		
		//feedback (styled to remove the list)
        final FeedbackPanel feedback = new FeedbackPanel("feedback");
        feedback.setOutputMarkupId(true);
        form.add(feedback);
		
		//submit button
        IndicatingAjaxButton submitButton = new IndicatingAjaxButton("submit", form) {
        	
        	protected void onSubmit(AjaxRequestTarget target, Form form) {

				//get the model (already validated)
        		SimpleText simpleText = (SimpleText) form.getModelObject();
        		
        		
        		//create a ProfileImageExternal record for this user and URL
        		ProfileImageExternal ext = new ProfileImageExternal(
        				userId,
        				simpleText.getText(),
        				null);
        		
        		//save it
        		if(profile.saveExternalImage(ext)) {
        			System.out.println("saved");
        		} else {
        			System.out.println("crap");
        		}
        		
        		
        		
        		
        	};
        	
        	// update feedback panel if validation failed
        	protected void onError(AjaxRequestTarget target, Form form) { 
        		System.out.println("validation failed");
        	    target.addComponent(feedback); 
        	} 
    		
        };
        submitButton.setModel(new ResourceModel("button.upload"));
		form.add(submitButton);
		
		
		//add form to page
		add(form);
    }

}


