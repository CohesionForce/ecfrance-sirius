/**
 * Copyright (c) 2008 Borland Software Corporation
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Dmitry Stadnik - initial API and implementation
 *    Obeo - Adaptations.
 */
package org.eclipse.sirius.diagram.ui.tools.api.figure;

import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.WeakHashMap;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.dom.svg.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.eclipse.draw2d.Figure;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.sirius.diagram.DiagramPlugin;
import org.eclipse.sirius.diagram.ui.tools.internal.figure.svg.InferringNamespaceContext;
import org.eclipse.sirius.diagram.ui.tools.internal.figure.svg.SVGUtils;
import org.eclipse.sirius.diagram.ui.tools.internal.figure.svg.SimpleImageTranscoder;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Display;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

//CHECKSTYLE:OFF
public class SVGFigure extends Figure {

    private String uri;

    private boolean failedToLoadDocument, specifyCanvasWidth = true, specifyCanvasHeight = true;

    private SimpleImageTranscoder transcoder;

    protected static WeakHashMap<String, Document> documentsMap = new WeakHashMap<String, Document>();

    public final String getURI() {
        return uri;
    }

    public final void setURI(String uri) {
        setURI(uri, true);
    }

    public void setURI(String uri, boolean loadOnDemand) {
        this.uri = uri;
        transcoder = null;
        failedToLoadDocument = false;
        if (loadOnDemand) {
            loadDocument();
        }
    }

    private void loadDocument() {
        transcoder = null;
        failedToLoadDocument = true;
        if (uri == null) {
            return;
        }
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        try {
            String documentKey = getDocumentKey();
            Document document;
            if (documentsMap.containsKey(documentKey))
                document = documentsMap.get(documentKey);
            else {
                document = factory.createDocument(uri);
                documentsMap.put(documentKey, document);
            }
            transcoder = new SimpleImageTranscoder(document);
            failedToLoadDocument = false;
        } catch (IOException e) {
            DiagramPlugin.getInstance().logError("Error loading SVG file", e);
        }
    }

    protected final Document getDocument() {
        if (failedToLoadDocument) {
            return null;
        }
        if (transcoder == null) {
            loadDocument();
        }
        return transcoder == null ? null : transcoder.getDocument();
    }

    /**
     * The key used to store the document.
     * 
     * @return the key.
     */
    protected String getDocumentKey() {
        return uri;
    }

    /**
     * Returns true if document was loaded without errors; tries to load
     * document if needed.
     */
    public final boolean checkContentAvailable() {
        return getDocument() != null;
    }

    private XPath getXPath() {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new InferringNamespaceContext(getDocument().getDocumentElement()));
        return xpath;
    }

    /**
     * Executes XPath query over the SVG document.
     */
    protected final NodeList getNodes(String query) {
        Document document = getDocument();
        if (document != null) {
            try {
                return (NodeList) getXPath().evaluate(query, document, XPathConstants.NODESET);
            } catch (XPathExpressionException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    /**
     * Reads color value from the document.
     */
    protected Color getColor(Element element, String attributeName) {
        if (getDocument() == null || getDocument() != element.getOwnerDocument()) {
            return null;
        }
        Color color = null;
        // Make sure that CSSEngine is available.
        BridgeContext ctx = transcoder.initCSSEngine();
        try {
            color = SVGUtils.toSWTColor(element, attributeName);
        } finally {
            if (ctx != null) {
                ctx.dispose();
            }
        }
        return color;
    }

    @Override
    protected void paintFigure(Graphics graphics) {
        super.paintFigure(graphics);
        Document document = getDocument();
        if (document == null) {
            return;
        }
        Image image = null;
        try {
            Rectangle r = getClientArea();
            transcoder.setCanvasSize(specifyCanvasWidth ? r.width : -1, specifyCanvasHeight ? r.height : -1);
            updateRenderingHints(graphics);
            BufferedImage awtImage = transcoder.getBufferedImage();
            if (awtImage != null) {
                image = toSWT(Display.getCurrent(), awtImage);
                graphics.drawImage(image, r.x, r.y);
            }
        } finally {
            if (image != null) {
                image.dispose();
            }
        }
    }

    protected void updateRenderingHints(Graphics graphics) {
        {
            int aa = SWT.DEFAULT;
            try {
                aa = graphics.getAntialias();
            } catch (Exception e) {
                // not supported
            }
            Object aaHint;
            if (aa == SWT.ON) {
                aaHint = RenderingHints.VALUE_ANTIALIAS_ON;
            } else if (aa == SWT.OFF) {
                aaHint = RenderingHints.VALUE_ANTIALIAS_OFF;
            } else {
                aaHint = RenderingHints.VALUE_ANTIALIAS_DEFAULT;
            }
            if (transcoder.getRenderingHints().get(RenderingHints.KEY_ANTIALIASING) != aaHint) {
                transcoder.getRenderingHints().put(RenderingHints.KEY_ANTIALIASING, aaHint);
                transcoder.contentChanged();
            }
        }
        {
            int aa = SWT.DEFAULT;
            try {
                aa = graphics.getTextAntialias();
            } catch (Exception e) {
                // not supported
            }
            Object aaHint;
            if (aa == SWT.ON) {
                aaHint = RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
            } else if (aa == SWT.OFF) {
                aaHint = RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
            } else {
                aaHint = RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT;
            }
            if (transcoder.getRenderingHints().get(RenderingHints.KEY_TEXT_ANTIALIASING) != aaHint) {
                transcoder.getRenderingHints().put(RenderingHints.KEY_TEXT_ANTIALIASING, aaHint);
                transcoder.contentChanged();
            }
        }
    }

    /**
     * Converts an AWT based buffered image into an SWT <code>Image</code>. This
     * will always return an <code>Image</code> that has 24 bit depth regardless
     * of the type of AWT buffered image that is passed into the method.
     * 
     * @param awtImage
     *            the {@link java.awt.image.BufferedImage} to be converted to an
     *            <code>Image</code>
     * @return an <code>Image</code> that represents the same image data as the
     *         AWT <code>BufferedImage</code> type.
     */
    protected static org.eclipse.swt.graphics.Image toSWT(Device device, BufferedImage awtImage) {
        // We can force bitdepth to be 24 bit because BufferedImage getRGB
        // allows us to always retrieve 24 bit data regardless of source color
        // depth.
        PaletteData palette = new PaletteData(0xFF0000, 0xFF00, 0xFF);
        ImageData swtImageData = new ImageData(awtImage.getWidth(), awtImage.getHeight(), 24, palette);
        // Ensure scansize is aligned on 32 bit.
        int scansize = (((awtImage.getWidth() * 3) + 3) * 4) / 4;
        WritableRaster alphaRaster = awtImage.getAlphaRaster();
        byte[] alphaBytes = new byte[awtImage.getWidth()];
        for (int y = 0; y < awtImage.getHeight(); y++) {
            int[] buff = awtImage.getRGB(0, y, awtImage.getWidth(), 1, null, 0, scansize);
            swtImageData.setPixels(0, y, awtImage.getWidth(), buff, 0);
            if (alphaRaster != null) {
                int[] alpha = alphaRaster.getPixels(0, y, awtImage.getWidth(), 1, (int[]) null);
                for (int i = 0; i < awtImage.getWidth(); i++) {
                    alphaBytes[i] = (byte) alpha[i];
                }
                swtImageData.setAlphas(0, y, awtImage.getWidth(), alphaBytes, 0);
            }
        }
        return new org.eclipse.swt.graphics.Image(device, swtImageData);
    }

    public final Rectangle2D getAreaOfInterest() {
        getDocument();
        return transcoder == null ? null : transcoder.getCanvasAreaOfInterest();
    }

    public void setAreaOfInterest(Rectangle2D value) {
        getDocument();
        if (transcoder != null) {
            transcoder.setCanvasAreaOfInterest(value);
        }
        repaint();
    }

    public final boolean isSpecifyCanvasWidth() {
        return specifyCanvasWidth;
    }

    public void setSpecifyCanvasWidth(boolean specifyCanvasWidth) {
        this.specifyCanvasWidth = specifyCanvasWidth;
        contentChanged();
    }

    public final boolean isSpecifyCanvasHeight() {
        return specifyCanvasHeight;
    }

    public void setSpecifyCanvasHeight(boolean specifyCanvasHeight) {
        this.specifyCanvasHeight = specifyCanvasHeight;
        contentChanged();
    }

    /**
     * Should be called when SVG document has been changed. It will be
     * re-rendered and figure will be repainted.
     */
    public void contentChanged() {
        getDocument();
        if (transcoder != null) {
            transcoder.contentChanged();
        }
        repaint();
    }

    protected SimpleImageTranscoder getTranscoder() {
        return transcoder;
    }

    protected boolean getSpecifyCanvasWidth() {
        return specifyCanvasWidth;
    }

    protected boolean getSpecifyCanvasHeight() {
        return specifyCanvasHeight;
    }
    // CHECKSTYLE:ON
}
