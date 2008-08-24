/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007 Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.calendar.impl;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TimeZone;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.calendar.api.RecurrenceRule;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeRange;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.util.DefaultEntityHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
* <p>ExclusionSeqRecurrenceRule is a rule which excludes specific sequence numbers from a list of instances.</p>
*/
public class ExclusionSeqRecurrenceRule
	implements RecurrenceRule
{
	/** Our logger. */
	private static Log M_log = LogFactory.getLog(ExclusionSeqRecurrenceRule.class);

	/** The list of sequence number (Integer) values to exclude. */
	protected List m_exclusions = null;

	/**
	* Construct.
	*/
	public ExclusionSeqRecurrenceRule()
	{
		m_exclusions = new Vector();

	}	// ExclusionSeqRecurrenceRule

	/**
	* Construct with these limits.
	* @param ranges The list of ranges to exclude
	*/
	public ExclusionSeqRecurrenceRule(List ranges)
	{
		m_exclusions = new Vector(ranges);

	}	// ExclusionSeqRecurrenceRule

	/**
	* Access the List of Integer sequence values excluded.
	* @return the List of Integer sequence values excluded.
	*/
	public List getExclusions() { return m_exclusions; }

	/**
	* Take values from this xml element
	* @param el The xml element.
	*/
	public void set(Element el)
	{
		// the children (time ranges)
		NodeList children = el.getChildNodes();
		final int length = children.getLength();
		for(int i = 0; i < length; i++)
		{
			Node child = children.item(i);
			if (child.getNodeType() != Node.ELEMENT_NODE) continue;
			Element element = (Element)child;

			// look for a time range
			if (element.getTagName().equals("exclude"))
			{
				try
				{
					m_exclusions.add(new Integer(element.getAttribute("sequence")));
				}
				catch (Exception e) { M_log.warn("set: while reading exclude sequence: " + e); }
			}
		}

	}	// set

	/**
	* Serialize the resource into XML, adding an element to the doc under the top of the stack element.
	* @param doc The DOM doc to contain the XML (or null for a string return).
	* @param stack The DOM elements, the top of which is the containing element of the new "resource" element.
	* @return The newly added element.
	*/
	public Element toXml(Document doc, Stack stack)
	{
		// add the "rule" element to the stack'ed element
		Element rule = doc.createElement("ex-rule");
		((Element)stack.peek()).appendChild(rule);

		// set the class name - old style for CHEF 1.2.10 compatibility
		rule.setAttribute("class", "org.chefproject.osid.calendar.ExclusionSeqRecurrenceRule");

		// set the rule class name w/o package, for modern usage
		rule.setAttribute("name", "ExclusionSeqRecurrenceRule");

		// set the ranges
		for (Iterator iSeq = m_exclusions.iterator(); iSeq.hasNext();)
		{
			Integer seq = (Integer) iSeq.next();

			Element exElement = doc.createElement("exclude");
			rule.appendChild(exElement);
			exElement.setAttribute("sequence", seq.toString());
		}

		return rule;

	}	// toXml

	/**
	* Return a List of all RecurrenceInstance objects generated by this rule within the given time range, based on the
	* prototype first range, in time order.
	* @param prototype The prototype first TimeRange.
	* @param range A time range to limit the generated ranges.
	* @return a List of RecurrenceInstance generated by this rule in this range.
	*/
	public List generateInstances(TimeRange prototype, TimeRange range, TimeZone timeZone)
	{
		return new Vector();

	}	// generateInstances

	/**
	* Remove from the ranges list any RecurrenceInstance excluded by this rule.
	* @param ranges The list (RecurrenceInstance) of ranges.
	*/
	public void excludeInstances(List ranges)
	{
		Vector rv = new Vector();
		
		for (Iterator iInstances = ranges.iterator(); iInstances.hasNext();)
		{
			RecurrenceInstance ri = (RecurrenceInstance) iInstances.next();

			if (!m_exclusions.contains(ri.getSequence()))
			{
				rv.add(ri);
			}
		}

		ranges.clear();
		ranges.addAll(rv);

	}

	/**
	 * {@inheritDoc}
	 */
	public String getFrequencyDescription()
	{
		return "xx";
	}

	/**
	 * {@inheritDoc}
	 */
	public Time getUntil()
	{
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public int getCount()
	{
		return 0;
	}

	/**
	 * {@inheritDoc}
	 */
	public int getInterval()
	{
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.calendar.api.RecurrenceRule#getContentHandler()
	 */
	public ContentHandler getContentHandler(Map<String,Object> services)
	{
		return new DefaultHandler()
		{
			/*
			 * (non-Javadoc)
			 * 
			 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
			 *      java.lang.String, java.lang.String, org.xml.sax.Attributes)
			 */
			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) throws SAXException
			{
				// the children (time ranges)
				if ("exclude".equals(qName))
				{
					try
					{
						m_exclusions.add(new Integer(attributes.getValue("sequence")));
					}
					catch (Exception e)
					{
						M_log.warn("set: while reading exclude sequence: " + e);
					}
				}
			}

		};
	}

}	// ExclusionSeqRecurrenceRule



