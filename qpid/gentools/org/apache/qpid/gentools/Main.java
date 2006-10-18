/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.gentools;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class Main
{
	private DocumentBuilder docBuilder;
	private AmqpVersionSet versionSet;
	private Generator generator;
	private AmqpDomainMap domainMap;
	private AmqpModel model;
	
	public Main() throws ParserConfigurationException
	{
		docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		versionSet = new AmqpVersionSet();
	}
	
	public void run(String[] args)
		throws 	IOException,
				SAXException,
				AmqpParseException,
				AmqpTypeMappingException,
				AmqpTemplateException,
				TargetDirectoryException,
				IllegalAccessException,
		    	InvocationTargetException
	{
		File[] modelTemplateFiles = new File[]{};
		File[] classTemplateFiles = new File[]{};
		File[] methodTemplateFiles = new File[]{};
		File[] fieldTemplateFiles = new File[]{};
		String outDir = "out";
		
		if (args[0].compareToIgnoreCase("-c") == 0)
		{
			// *** C++ generation ***
			generator = new CppGenerator(versionSet);
			domainMap = new AmqpDomainMap(generator);
			model = new AmqpModel(generator);			
//			classTemplateFiles = new File[]{ new File("templ.cpp/PropertyContentHeaderClass.tmpl") };
//			methodTemplateFiles = new File[]{ new File("templ.cpp/MethodBodyClass.tmpl") };
			outDir += ".cpp";
		}
		else if (args[0].compareToIgnoreCase("-j") == 0)
		{
			// *** Java generation ***
			generator = new JavaGenerator(versionSet);
			domainMap = new AmqpDomainMap(generator);
			model = new AmqpModel(generator);		
			modelTemplateFiles = new File[]
			    { new File("templ.java/MethodRegistryClass.tmpl") };
			classTemplateFiles = new File[]
			    { new File("templ.java/PropertyContentHeaderClass.tmpl") };
			methodTemplateFiles = new File[]
			    { new File("templ.java/MethodBodyClass.tmpl") };
			outDir += ".java";
		}
		else
		{
			System.err.println("ERROR: Required argument specifying language (C++ [-c] or Java [-j]) missing.");
			usage();		
		}
		
		// 1. Suck in all the XML spec files provided on the command line.
		for (int i=1; i<args.length; i++)
		{
			File f = new File(args[i]);
			if (f.exists())
			{
				// 1a. Initialize dom
				System.out.print("File: " + args[i]);
				Document doc = docBuilder.parse(new File(args[i]));
				Node amqpNode = Utils.findChild(doc, Utils.ELEMENT_AMQP);
				
				// 1b. Extract version (major and minor) from the XML file
				int major = Utils.getNamedIntegerAttribute(amqpNode, Utils.ATTRIBUTE_MAJOR);
				int minor = Utils.getNamedIntegerAttribute(amqpNode, Utils.ATTRIBUTE_MINOR);
				AmqpVersion version = new AmqpVersion(major, minor);
				System.out.println(" Found version " + version.toString() + ".");
				versionSet.add(version);
				
				// 1c. Extract domains
				domainMap.addFromNode(amqpNode, 0, version);
				
				// 1d. Extract class/method/field heirarchy
				model.addFromNode(amqpNode, 0, version);
			}
			else
				System.err.println("ERROR: AMQP XML file \"" + args[i] + "\" not found.");
		}
// *** DEBUG INFO ***  Uncomment bits from this block to see lots of stuff....
//		System.out.println();
//		System.out.println("*** Debug output ***");
//		System.out.println();
//		versionSet.print(System.out, 0, 2);
//		System.out.println();
//		domainMap.print(System.out, 0, 2);
//		System.out.println();
//		model.print(System.out, 0, 2);
//		System.out.println();
//		System.out.println("*** End debug output ***");
//		System.out.println();
		
		// 2. Load up all templates
		generator.initializeTemplates(modelTemplateFiles, classTemplateFiles,
			methodTemplateFiles, fieldTemplateFiles);
		
		// 3. Generate output
		generator.generate(new File(outDir));
		
		System.out.println("Files generated: " + generator.getNumberGeneratedFiles());
		System.out.println("Done.");
	}

	public static void main(String[] args)
	{
		if (args.length < 2)
			usage();
		try { new Main().run(args); }
		catch (IOException e) { e.printStackTrace(); }
		catch (ParserConfigurationException e) { e.printStackTrace(); }
		catch (SAXException e) { e.printStackTrace(); }
		catch (AmqpParseException e) { e.printStackTrace(); }
		catch (AmqpTypeMappingException e) { e.printStackTrace(); }
		catch (AmqpTemplateException e) { e.printStackTrace(); }
		catch (TargetDirectoryException e) { e.printStackTrace(); }
		catch (IllegalAccessException e) { e.printStackTrace(); }
		catch (InvocationTargetException e) { e.printStackTrace(); }
	}

	public static void usage()
	{
		System.out.println("AMQP XML generator v.0.0");
		System.out.println("Usage: Main -c|-j filename [filename ...]");
		System.out.println("       where -c flags C++ generation.");
		System.out.println("             -j flags Java generation.");
		System.out.println("             filename is a space-separated list of files to be parsed.");
		System.exit(0);
	}
}
