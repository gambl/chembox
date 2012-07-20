package cs.manchester.ac.uk.chembox;

import de.tudarmstadt.ukp.wikipedia.parser.ParsedPage;
import de.tudarmstadt.ukp.wikipedia.parser.Template;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParser;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParserFactory;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.sparql.resultset.ResultSetFormat;
import com.hp.hpl.jena.sparql.util.FmtUtils;
import com.hp.hpl.jena.rdf.model.*;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.URLEncoder;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

/**
 * Main program to generate the chembox rdf dump.
 * 
 */

public class chembox {

	public static String wikiUrl = "http://en.wikipedia.org/wiki/";
	private static String filename;
	private static String fileType;
	private static String page;
	private static String directory = "";
	private static Boolean cmdline = false;
	private static Boolean redirects = false;

	public static void main(String[] args) throws Exception {

		Options options = new Options();
		options.addOption("f", true, "file listing URLs wikipages to proccess");
		options.addOption("t", true,
				"file type of the inpunt file, text, csv, jena (result set where URL is a result var 'page'");
		options.addOption("p", true, "URL of a wiki-page for proccessing");
		options.addOption("d", true, "directory to output to (default is cwd)");
		options.addOption("o", false,
				"Output to command line (overrides and prevents output to file");
		options.addOption("r", false,
				"Attempt to deal with Wikipedia redirects");

		CommandLineParser parser = new PosixParser();
		CommandLine cmd = parser.parse(options, args);

		ArrayList<String> uris = new ArrayList<String>();

		if (cmd.hasOption('o')) {
			cmdline = true;
		}

		if (cmd.hasOption('d')) {
			directory = cmd.getOptionValue('d');
		}
		if (cmd.hasOption('r')) {
			redirects = true;
		}

		if (cmd.hasOption('f')) {
			if (cmd.hasOption('t')) {
				filename = cmd.getOptionValue('f');
				fileType = cmd.getOptionValue('t');
				if (fileType.equals("jena")) {
					ResultSet rs = ResultSetFactory.load(filename,
							ResultSetFormat.syntaxRDF_N3);
					uris = listFromResultSet(rs, "page");
				}
				if (fileType.equals("text")) {
					uris = listFromTextFile(filename);
				}
			}
		} else if (cmd.hasOption('p')) {
			page = cmd.getOptionValue('p');
			uris.add(page);
		}

		proccessUriList(uris, redirects);

	}// main

	private static ArrayList<String> listFromTextFile(String filename)
			throws Exception {

		BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(filename)));
		ArrayList<String> uris = new ArrayList<String>();
		String line;
		while ((line = br.readLine()) != null) {
			uris.add(line);
		}
		return uris;
	}

	public static void proccessUriList(ArrayList<String> uris, boolean redirects)
			throws Exception {

		for (String uri : uris) {
			try {
				String pageTitle = uri
						.substring(wikiUrl.length(), uri.length());

				WikiPage wikiPage = fetchPage(pageTitle, redirects);
				if (wikiPage == null) {
					System.out.println(pageTitle + " failed");
					continue;
				}
				// System.out.println(fullPageString);
				// if the page string is empty then we probably had a 404
				String fullPageString = wikiPage.getFullPageString();
				if (fullPageString.length() == 0) {
					System.out.println(pageTitle + " failed");
					continue;
				}

				Model model = generateModelFromPage(wikiPage);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				model.write(baos, "TURTLE");

				// URI decode the page title to create the file name

				if (cmdline) {
					String out = baos.toString();
					System.out.println(out);
				} else {

					try {
						if (directory != null && !directory.equals("")) {
							model.write(new FileOutputStream(directory + "/"
									+ pageTitle + ".ttl"), "TURTLE");
						} else {
							model.write(
									new FileOutputStream(pageTitle + ".ttl"),
									"TURTLE");
						}
					} catch (Exception e) {
						System.out.println("Failed to write: " + pageTitle
								+ " " + e.getCause());
					}
				}

			} catch (Exception e) {
				System.out.println("Failed on " + uri + " " + e.getCause());
			}
		}
	}

	// Extracts the specific 'resource' of a ResultSet into an ArrayList
	public static ArrayList<String> listFromResultSet(ResultSet rs,
			String resource) {

		ArrayList<String> uris = new ArrayList<String>();
		while (rs.hasNext()) {

			QuerySolution sol = rs.next();
			Resource pageResource = sol.getResource(resource);
			String pageString = pageResource.toString();
			uris.add(pageString);
		}
		return uris;
	}

	public static Model generateModelFromPage(WikiPage wikiPage)
			throws Exception {

		String fullPageString = wikiPage.getFullPageString();
		String pageTitle = wikiPage.getPageTitle();
		// set up an individually parametrized MediaWikiParser
		MediaWikiParserFactory pf = new MediaWikiParserFactory();
		MediaWikiParser parser = pf.createParser();
		// System.out.println(fullPageString);
		ParsedPage pp = parser.parse(fullPageString);

		List<Template> templates = pp.getTemplates();

		Resource dbpChemResource = ResourceFactory
				.createResource("http://dbpedia.org/resource/" + pageTitle);
		Resource chemboxResource = ResourceFactory
				.createResource("http://purl.org/net/chembox/" + pageTitle);
		Property sameas = ResourceFactory
				.createProperty("http://www.w3.org/2002/07/owl#sameAs");
		Property name = ResourceFactory
				.createProperty("http://dbpedia.org/resource/Template:Chembox:wikiPageHasName");
		Literal pageLiteral = ResourceFactory.createPlainLiteral(URLDecoder
				.decode(pageTitle));
		Property wikiUsesTemplate = ResourceFactory
				.createProperty("http://dbpedia.org/property/wikiPageUsesTemplate");
		Resource chemboxTemplate = ResourceFactory
				.createResource("http://dbpedia.org/resource/Template:Chembox");

		// create a statement to say this is the same as its dbpedia counterpart
		Statement same = ResourceFactory.createStatement(chemboxResource,
				sameas, dbpChemResource);

		Statement n = ResourceFactory.createStatement(chemboxResource, name,
				pageLiteral);

		Statement templateStatement = ResourceFactory.createStatement(
				chemboxResource, wikiUsesTemplate, chemboxTemplate);

		Iterator<Template> templatesIterator = templates.iterator();
		Model model = ModelFactory.createDefaultModel();
		model.add(same);
		model.add(n);
		model.add(templateStatement);
		boolean seenChembox = false;
		// For each template
		while (templatesIterator.hasNext()) {

			Template template = templatesIterator.next();
			// switch for special template names like cite_journal TODO
			if (template.getName().equalsIgnoreCase("Chembox"))
				seenChembox = true;

			List<String> parameterList = template.getParameters();
			Iterator<String> parameterListIterator = parameterList.iterator();

			// For each parameter list
			while (parameterListIterator.hasNext()) {

				String parameter = parameterListIterator.next();

				// Does it contain =
				if (parameter.indexOf('=') != -1) {

					if (parameter.indexOf('=') == parameter.length() - 1)
						continue;// Then this has no value

					// If ok split at =
					String var = parameter.substring(0, parameter.indexOf('='))
							.trim();
					String value = parameter.substring(
							parameter.indexOf('=') + 1, parameter.length())
							.trim();

					if (value.contains("&lt;ref")) {

						value = value.substring(0, value.indexOf("&lt;ref"));
					}
					// If value is reference to another TEMPLATE ... do
					// something
					// For now just skip TODO

					if (value.contains("TEMPLATE")) {
						continue;

					}
					if (value.endsWith("}}")) {
						// Trim it off and then check whether it's empty?
					}

					if (var.equals("MeltingPt") || var.equals("BoilingPt")) {
						if (value.contains(",")) {
							String firstvalue = value.substring(0,
									value.indexOf(","));
							String secondvalue = value.substring(
									value.indexOf(","), value.length());
							try {
								addToModel(chemboxResource, var, firstvalue,
										model);
								addToModel(chemboxResource, var, secondvalue,
										model);
							} catch (Exception e) {
								System.out
										.println("http://dbpedia.org/resource/"
												+ pageTitle + " lost " + var
												+ "= " + value);
							}
						}
					} else {
						try {

							addToModel(chemboxResource, var, value, model);

						} catch (Exception e) {
							System.out
									.println("http://dbpedia.org/resource/"
											+ pageTitle + " lost " + var + "= "
											+ value);
						}
					}

				}//
			}// parameterListIterator
		}// templateIterator
		if (wikiPage.isRedirected()) {
			Property prop = ResourceFactory
					.createProperty("http://www.w3.org/2002/07/owl#sameAs");
			Resource resource = ResourceFactory
					.createResource("http://dbpedia.org/resource/"
							+ wikiPage.getRedirectedTo());

			Statement s = ResourceFactory.createStatement(chemboxResource,
					prop, resource);
			model.add(s);
		}

		return model;
	}

	public static void addToModel(Resource chemboxResource, String var,
			String value, Model model) {
		Property prop = ResourceFactory.createProperty(
				"http://dbpedia.org/resource/Template:Chembox:",
				URLEncoder.encode(var));

		value = URLDecoder.decode(value);
		Literal literal = ResourceFactory.createPlainLiteral(value);

		Statement s = ResourceFactory.createStatement(chemboxResource, prop,
				literal);
		// System.out.println(s);
		model.add(s);

	}

	public static WikiPage fetchPage(String pageTitle, boolean redirects) {

		String urlString = "http://en.wikipedia.org/w/index.php?title="
				+ pageTitle + "&action=edit";

		String fullPageString = new String();

		WikiPage wikiPage = null;

		try {
			URL url = new URL(urlString);
			BufferedReader in = new BufferedReader(new InputStreamReader(
					url.openStream()));

			String inputLine;
			// Are we dealing with redirects? It's expensive/messy to check atm
			if (!redirects) {
				while ((inputLine = in.readLine()) != null) {
					fullPageString += inputLine;
				}
			} else {

				while ((inputLine = in.readLine()) != null) {
					// This is horribly inefficient to test this for every
					// line when it is very unlikely to occurr
					if ((inputLine.indexOf("wpTextbox1\">#REDIRECT")) > -1) {
						System.out.println(inputLine);

						Pattern pattern = Pattern
								.compile("#REDIRECT \\Q[[\\E(.*)\\Q]]\\E");
						Matcher matcher = pattern.matcher(inputLine);
						String redirect = "";
						boolean found = false;
						if (matcher.find()) {
							redirect = matcher.group(1);
							found = true;
						}
						if (!found) {
							System.out.println("No match found.");
						}

						System.out.println(pageTitle + " redirected to "
								+ redirect);
						wikiPage = fetchPage(redirect, false);
						wikiPage.setRedirected(true);
						wikiPage.setRedirectedFrom(pageTitle);
						wikiPage.setRedirectedTo(redirect);
						wikiPage.setPageTitle(pageTitle);
						return wikiPage;
					} else {
						fullPageString += inputLine;
					}
				}
			}
			wikiPage = new WikiPage(fullPageString, pageTitle);

			in.close();

		} catch (Exception e) {

			System.out.println(urlString + " failed: " + e.getMessage());
		}

		return wikiPage;
	}

}
