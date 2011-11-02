package converters.dbgap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.bind.JAXBException;

import org.apache.log4j.Logger;
import org.molgenis.core.OntologyTerm;
import org.molgenis.organization.Investigation;
import org.molgenis.pheno.Measurement;
import org.molgenis.pheno.ObservableFeature;
import org.molgenis.pheno.Panel;
import org.molgenis.protocol.Protocol;

import app.CsvExport;
import converters.dbgap.DbGapService;
import converters.dbgap.jaxb.Study;
import converters.dbgap.jaxb.data_dict.Data_Dict;
import converters.dbgap.jaxb.data_dict.Value;
import converters.dbgap.jaxb.data_dict.Variable;
import converters.dbgap.jaxb.var_report.Stat;
import converters.dbgap.jaxb.var_report.Var_Report;
import converters.dbgap.jaxb.var_report.VariableSummary;

public class DbGapToPheno
{
	public static void main(String[] args) throws Exception
	{
		String outputFolder = "d:/Data/dbgap/";
		String dbgapUrl = outputFolder + "FTP_Table_of_Contents.xml";
		DbGapService dbgap = new DbGapService(new File(dbgapUrl).toURL(), new File(outputFolder));

		int count = 1;

		// get studies
		List<Study> studies = dbgap.listStudies();

		// filter out last versions only
		Map<String, Study> lastversions = new TreeMap<String, Study>();
		for (Study s : studies)
		{
			// System.out.println("filtering "+ s.id + " "+s.version+ " "
			// +s.description);
			if (lastversions.get(s.id) == null
					|| Integer.parseInt(lastversions.get(s.id).version.replace("v", "")) < Integer.parseInt(s.version
							.replace("v", ""))) lastversions.put(s.id, s);
		}

		// caching all files
//		for (Data_Dict vr : dbgap.listDictionaries())
//		{
//			File f =  new File(outputFolder + "download/" + vr.id + ".xml");
//			if(!f.exists()) downloadFile(vr.url,f);
//		}
//		for (Var_Report vr : dbgap.listVariableReports())
//		{
//			File f = new File(outputFolder + "download/" + vr.dataset_id + ".xml");
//			if(!f.exists()) downloadFile(vr.url, f);
//		}

		// download the last versions
		System.out.println("lastversions = " + lastversions.size());
		for (Study s : lastversions.values())
		{
			DbGapToPheno convertor = new DbGapToPheno();

			// writing the data_dicts
			File dir = new File(outputFolder + s.id);
			System.out.println("converting " + s.id + " " + s.version + " " + s.description + " to "+dir);
			dbgap.loadDictionaries(s);
			dbgap.loadVariableReports(s);

			convertor.read(s);

			dir.mkdirs();
			// System.out.println(convertor.toString());
			convertor.write(dir);

			// debug purposes only
			count++;
			if (count > 10) break;
		}

	}

	public void write(File dir) throws Exception
	{
		new CsvExport().exportAll(dir, investigations, protocols, features, new ArrayList(terms.values()), panels);
	}

	public static void downloadFile(URL url, File destination) throws IOException
	{
		logger.debug("downloading " + url + " to " + destination);
		BufferedInputStream in = null;
		BufferedOutputStream out = null;
		try
		{
			URLConnection urlc = url.openConnection();

			in = new BufferedInputStream(urlc.getInputStream());
			out = new BufferedOutputStream(new FileOutputStream(destination));

			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0)
			{
				out.write(buf, 0, len);
			}

		}
		finally
		{
			if (in != null) try
			{
				in.close();
			}
			catch (IOException ioe)
			{
				ioe.printStackTrace();
			}
			if (out != null) try
			{
				out.close();
			}
			catch (IOException ioe)
			{
				ioe.printStackTrace();
			}
		}

	}

	/**
	 * Read the dbGaP study into the convertor
	 * 
	 * @param s
	 * @throws JAXBException
	 * @throws IOException
	 */
	public void read(Study s) throws JAXBException, IOException
	{
		Investigation i = new Investigation();
		i.setDescription(s.description);
		i.setName(s.id + "." + s.version);
		investigations.add(i);

		// data dictionaries = protocols + variables + features (+ ontologies)
		for (Data_Dict dd : s.dictionaries)
		{
			Protocol p = new Protocol();
			p.setName(dd.description);
			p.setInvestigation_Name(i.getName());
			p.setName(dd.id);
			protocols.add(p);

			for (Variable var : dd.variables)
			{
				Measurement f = new Measurement();
				f.setInvestigation_Name(i.getName());
				f.setName(var.name.toLowerCase());
				f.setDescription(var.description);
				// todo: add annotation feature NVT type?
				if (var.type != null) f.setDescription(f.getDescription() + " Type=" + var.type + ".");
				if (var.logical_min != null) f.setDescription(f.getDescription() + " LogicalMin=" + var.logical_min
						+ ".");
				if (var.logical_min != null) f.setDescription(f.getDescription() + " LogicalMax=" + var.logical_max
						+ ".");
				f.setUnit_Name(var.unit);

				features.add(f);
				List<String> f_labels = p.getFeatures_Name();
				f_labels.add(f.getName());
				p.setFeatures_Name(f_labels);

				if (var.unit != null && terms.get(var.unit) == null)
				{
					OntologyTerm t = new OntologyTerm();
					t.setName(var.unit);
					//t.setInvestigationLabel(i.getName());

					if(terms.containsKey(var.unit)) logger.warn("duplicate term "+var.unit);
					terms.put(var.unit, t);
				}

				if (var.values.size() > 0)
				{
					for (Value v : var.values)
					{
						OntologyTerm code = new OntologyTerm();
						code.setName(v.code);
						code.setDefinition(v.value);
						f.getValueCodesLabels().add(code.getTerm());
						
						//give error on duplicate term
						if(terms.containsKey(v.code)) logger.warn("duplicate term "+v.code);
						terms.put(v.code, code);
					}

				}

			}
		}

		// var report = observedValues, protocolApplication, panels
		Panel total_panel = new Panel();
		total_panel.setName("total");
		total_panel.setInvestigationLabel(i.getName());
		
		Panel cases_panel = new Panel();
		cases_panel.setName("cases");
		cases_panel.setInvestigationLabel(i.getName());
		
		Panel controls_panel = new Panel();
		controls_panel.setName("controls");
		controls_panel.setInvestigationLabel(i.getName());
		
		this.panels.add(total_panel);
		this.panels.add(cases_panel);
		this.panels.add(controls_panel);

		for (Var_Report vr : s.reports)
		{
			logger.debug("var_report " + vr.dataset_id);

			for (VariableSummary vs : vr.variables)
			{
				if (vs.total != null) addStatsToPanel(total_panel, i, vs, vs.total.stats);
				if (vs.cases != null) addStatsToPanel(cases_panel, i, vs, vs.cases.stats);
				if (vs.controls != null) addStatsToPanel(controls_panel, i, vs, vs.controls.stats);
			}
		}
	}

	/** Is there an ontology for these stat terms? */
	private void addStatsToPanel(Panel panel, Investigation investigation, VariableSummary vs, List<Stat> stats)
	{
		for (Stat stat : stats)
		{
			if (stat.n != null) addInferredValue(panel, investigation, vs, stat.n, "n");
			if (stat.nulls != null) addInferredValue(panel, investigation, vs, stat.nulls, "nulls");
			if (stat.invalid_values != null) addInferredValue(panel, investigation, vs, stat.invalid_values,
					"invalid_values");
			if (stat.special_values != null) addInferredValue(panel, investigation, vs, stat.special_values,
					"special_values");
			if (stat.mean != null) addInferredValue(panel, investigation, vs, stat.mean, "mean");
			if (stat.mean_count != null) addInferredValue(panel, investigation, vs, stat.mean_count, "mean_count");
			if (stat.sd != null) addInferredValue(panel, investigation, vs, stat.sd, "sd");
			if (stat.median != null) addInferredValue(panel, investigation, vs, stat.median, "median");
			if (stat.median_count != null) addInferredValue(panel, investigation, vs, stat.median_count, "median_count");
			if (stat.min != null) addInferredValue(panel, investigation, vs, stat.min, "min");
			if (stat.min_count != null) addInferredValue(panel, investigation, vs, stat.min_count, "min_count");
			if (stat.max != null) addInferredValue(panel, investigation, vs, stat.max, "max");
			if (stat.max_count != null) addInferredValue(panel, investigation, vs, stat.max_count, "max_count");
		}
	}

	private void addInferredValue(Panel p, Investigation i, VariableSummary vs, String value, String inferenceType)
	{
		if (terms.get(inferenceType) == null)
		{
			OntologyTerm t = new OntologyTerm();
			t.setName(inferenceType);
			//t.setInvestigation_Name(i.getName());
			terms.put(inferenceType, t);
		}

		InferredValue v = new InferredValue();
		v.setInvestigationLabel(i.getName());
		v.setObservableFeatureLabel(vs.var_name.toLowerCase());
		v.setValue(value);
		v.setInferenceTypeLabel(inferenceType);
		v.setObservationTargetLabel(p.getName());
		//System.out.println("infered value " + v);
		this.inferredValues.add(v);

	}

	public String toString()
	{
		String result = "";
		for (Investigation i : investigations)
			result += i + "\n";
		for (Protocol p2 : protocols)
			result += p2 + "\n";
		for (ObservableFeature f2 : features)
			result += f2 + "\n";
		for (OntologyTerm t : terms.values())
			result += t + "\n";
		for (Panel p : panels)
			result += p + "\n";
		for (InferredValue i : inferredValues)
			result += i + "\n";

		return result;
	}
	
	static Logger logger = Logger.getLogger(DbGapToPheno.class);

	List<Investigation> investigations = new ArrayList<Investigation>();
	List<Protocol> protocols = new ArrayList<Protocol>();
	List<ObservableFeature> features = new ArrayList<ObservableFeature>();
	Map<String, OntologyTerm> terms = new TreeMap<String, OntologyTerm>();
	List<Panel> panels = new ArrayList<Panel>();
	List<InferredValue> inferredValues = new ArrayList<InferredValue>();

}
