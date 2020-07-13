package com.overops.plugins.jenkins.query;

import java.util.List;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import com.takipi.api.client.ApiClient;
import com.takipi.api.client.RemoteApiClient;
import com.takipi.api.client.data.service.SummarizedService;
import com.takipi.api.client.util.client.ClientUtil;
import com.takipi.api.core.url.UrlClient.Response;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

//DescriptorImpl governs the global config settings

@Extension
@Symbol("OverOpsQuery")
public final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
	private String overOpsAppURL;
	private String overOpsURL;
	private String overOpsSID;
	private Secret overOpsAPIKey;

	public DescriptorImpl() {
		super(QueryOverOps.class);
		load();
	}

	@Override
	public boolean isApplicable(Class<? extends AbstractProject> aClass) {
		return true;
	}

	@Override
	public String getDisplayName() {
		return "OverOps Quality Report";
	}

	// Allows for persisting global config settings in JSONObject
	@Override
	public boolean configure(StaplerRequest req, JSONObject formData) {
		JSONObject QueryOverOpsJson = formData.getJSONObject("QueryOverOps");
		overOpsAppURL = QueryOverOpsJson.getString("overOpsAppURL");
		overOpsURL = QueryOverOpsJson.getString("overOpsURL");
		overOpsSID = QueryOverOpsJson.getString("overOpsSID");
		overOpsAPIKey = Secret.fromString(QueryOverOpsJson.getString("overOpsAPIKey"));
		save();
		return false;
	}

	public String getOverOpsAppURL() {
		return overOpsAppURL;
	}

	public String getOverOpsURL() {
		return overOpsURL;
	}

	public String getOverOpsSID() {
		return overOpsSID;
	}

	public Secret getOverOpsAPIKey() {
		return overOpsAPIKey;
	}
	
	private boolean hasAccessToService(ApiClient apiClient, String serviceId) {
		
		List<SummarizedService> services;
		
		try {
			services = ClientUtil.getEnvironments(apiClient);
		} catch (Exception e) {
			System.err.println(e);
			return false;
		}
		

		for (SummarizedService service : services) {
			if (service.id.equals(serviceId)) {
				return true;
			}
		}
		
		return false;

	}
	
	/**
	 * Tests to make sure the global settings for the Jenkins plugin pass checks
	 * 1st check is the URL for the APIs
	 * 2nd check is the serviceID to make sure the API token provided has access
	 * @param overOpsURL
	 * @param overOpsSID
	 * @param overOpsAPIKey
	 * @return
	 */
	@POST
	public FormValidation doTestConnection(@QueryParameter("overOpsAppURL") final String overOpsAppURL,
			@QueryParameter("overOpsURL") final String overOpsURL,
			@QueryParameter("overOpsSID") final String overOpsSID,
			@QueryParameter("overOpsAPIKey") final Secret overOpsAPIKey) {

		if (overOpsAppURL == null || overOpsAppURL.isEmpty()) {
			return FormValidation.error("OverOps Application URL is empty");
		}

		if (overOpsURL == null || overOpsURL.isEmpty()) {
			return FormValidation.error("OverOps API URL is empty");
		}

		// Admin permission check
		Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

		try {
			String apiKey = Secret.toString(overOpsAPIKey);
			RemoteApiClient apiClient = (RemoteApiClient) RemoteApiClient.newBuilder().setHostname(overOpsURL).setApiKey(apiKey).build();
			
			Response<String> response = apiClient.testConnection();
			    
			boolean testConnection = (response == null) || (response.isBadResponse());
			boolean testService = ((overOpsSID == null) || (hasAccessToService(apiClient, overOpsSID))); 
			
			if (testConnection) {
				int code;
				
				if (response != null) {
					code = response.responseCode;
				} else {
					code = -1;
				}

				return FormValidation.error("Unable to connect to API server. Code: " + code);
			}
			
			if (!testService) {
				return FormValidation.error("API key has no access to environment " + overOpsSID);
			}	
			
			return FormValidation.ok("Connection Successful.");
		} catch (Exception e) {
			return FormValidation.error(e, "REST API error : " + e.getMessage());
		}
	}
}
