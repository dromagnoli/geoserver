//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference
// Implementation, v2.2.8-b130911.1802
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2018.12.17 at 04:13:52 PM PST
//

package org.geoserver.mapml.xml;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;

/**
 * Java class for anonymous complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;choice maxOccurs="unbounded" minOccurs="0"&gt;
 *         &lt;element ref="{}input"/&gt;
 *         &lt;element ref="{}datalist"/&gt;
 *         &lt;group ref="{}links"/&gt;
 *         &lt;element ref="{}select"/&gt;
 *         &lt;element ref="{}label"/&gt;
 *       &lt;/choice&gt;
 *       &lt;attribute name="units" type="{}projType" /&gt;
 *       &lt;attribute name="action" type="{http://www.w3.org/2001/XMLSchema}anyURI" /&gt;
 *       &lt;attribute name="method"&gt;
 *         &lt;simpleType&gt;
 *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}token"&gt;
 *             &lt;enumeration value="get"/&gt;
 *             &lt;enumeration value="GET"/&gt;
 *           &lt;/restriction&gt;
 *         &lt;/simpleType&gt;
 *       &lt;/attribute&gt;
 *       &lt;attribute name="enctype"&gt;
 *         &lt;simpleType&gt;
 *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}token"&gt;
 *             &lt;enumeration value="application/x-www-form-urlencoded"/&gt;
 *           &lt;/restriction&gt;
 *         &lt;/simpleType&gt;
 *       &lt;/attribute&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
        name = "",
        propOrder = {"inputOrDatalistOrLink"})
@XmlRootElement(name = "map-extent", namespace = "http://www.w3.org/1999/xhtml")
public class Extent {

    @XmlElements({
        @XmlElement(
                name = "map-input",
                type = Input.class,
                namespace = "http://www.w3.org/1999/xhtml"),
        @XmlElement(
                name = "map-datalist",
                type = Datalist.class,
                namespace = "http://www.w3.org/1999/xhtml"),
        @XmlElement(
                name = "map-link",
                type = Link.class,
                namespace = "http://www.w3.org/1999/xhtml"),
        @XmlElement(
                name = "map-select",
                type = Select.class,
                namespace = "http://www.w3.org/1999/xhtml"),
        @XmlElement(
                name = "map-label",
                type = Label.class,
                namespace = "http://www.w3.org/1999/xhtml")
    })
    protected List<Object> inputOrDatalistOrLink;

    @XmlAttribute(name = "units")
    @XmlSchemaType(name = "anySimpleType")
    protected String units;

    @XmlAttribute(name = "label")
    @XmlSchemaType(name = "anySimpleType")
    protected String label;

    @XmlAttribute(name = "checked")
    @XmlSchemaType(name = "anySimpleType")
    protected String checked = "checked";

    @XmlAttribute(name = "hidden")
    @XmlSchemaType(name = "anySimpleType")
    protected String hidden = "hidden";

    /**
     * Gets the value of the inputOrDatalistOrLink property.
     *
     * <p>This accessor method returns a reference to the live list, not a snapshot. Therefore any
     * modification you make to the returned list will be present inside the JAXB object. This is
     * why there is not a <CODE>set</CODE> method for the inputOrDatalistOrLink property.
     *
     * <p>For example, to add a new item, do as follows:
     *
     * <pre>
     *    getInputOrDatalistOrLink().add(newItem);
     * </pre>
     *
     * <p>Objects of the following type(s) are allowed in the list {@link Input } {@link Datalist }
     * {@link Link } {@link Select } {@link Label }
     */
    public List<Object> getInputOrDatalistOrLink() {
        if (inputOrDatalistOrLink == null) {
            inputOrDatalistOrLink = new ArrayList<>();
        }
        return this.inputOrDatalistOrLink;
    }

    /**
     * Gets the value of the units property.
     *
     * @return possible object is {@link String }
     */
    public String getUnits() {
        return units;
    }

    /**
     * Sets the value of the units property.
     *
     * @param value allowed object is {@link String }
     */
    public void setUnits(String value) {
        this.units = value;
    }

    /**
     * Gets the value of the label property.
     *
     * @return possible object is {@link String }
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the value of the label property.
     *
     * @param value allowed object is {@link String }
     */
    public void setLabel(String value) {
        this.label = value;
    }

    /**
     * Gets the value of the checked property.
     *
     * @return possible object is {@link String }
     */
    public String getChecked() {
        return checked;
    }

    /**
     * Sets the value of the checked property.
     *
     * @param value allowed object is {@link String }
     */
    public void setChecked(String value) {
        this.checked = value;
    }

    /**
     * Gets the value of the hidden property.
     *
     * @return possible object is {@link String }
     */
    public String getHidden() {
        return hidden;
    }

    /**
     * Sets the value of the hidden property.
     *
     * @param value allowed object is {@link String }
     */
    public void setHidden(String value) {
        this.hidden = value;
    }
}
