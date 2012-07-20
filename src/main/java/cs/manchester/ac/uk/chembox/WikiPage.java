package cs.manchester.ac.uk.chembox;

public class WikiPage{
	
	String fullPageString;
	boolean redirected = false;
	String pageTitle;
	String redirectedFrom;
	String redirectedTo;
	
	public WikiPage(String pageString, String title){
		fullPageString = pageString;
		pageTitle = title;
		
	}
	
	public String getFullPageString() {
		return fullPageString;
	}

	public void setFullPageString(String fullPageString) {
		this.fullPageString = fullPageString;
	}

	public boolean isRedirected() {
		return redirected;
	}

	public void setRedirected(boolean redirected) {
		this.redirected = redirected;
	}

	public String getPageTitle() {
		return pageTitle;
	}

	public void setPageTitle(String pageTitle) {
		this.pageTitle = pageTitle;
	}

	public String getRedirectedFrom() {
		return redirectedFrom;
	}

	public void setRedirectedFrom(String redirectedFrom) {
		this.redirectedFrom = redirectedFrom;
	}

	public String getRedirectedTo() {
		return redirectedTo;
	}

	public void setRedirectedTo(String redirectedTo) {
		this.redirectedTo = redirectedTo;
	}
	
}