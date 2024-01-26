package org.jahia.se.modules.opengraph;

import net.htmlparser.jericho.*;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.RenderFilter;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.util.List;

@Component(service = RenderFilter.class)
public class OpenGraphFilter  extends AbstractFilter {
    public static final Logger logger = LoggerFactory.getLogger(OpenGraphFilter.class);

    private final static String OPENGRAPH_MODULE="open-graph";
    private final static String OPENGRAPH_MIXIN="jmix:openGraph";

    @Activate
    public void activate() {
        setPriority(0);// -1 launch after addStuff
        setApplyOnModes("live");//,preview
//        setApplyOnConfigurations("page");
        setApplyOnTemplateTypes("html");
        setSkipOnConfigurations("include,wrapper");//?
    }

    @Override
    public String execute(String previousOut, RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        String output = super.execute(previousOut, renderContext, resource, chain);
        boolean isInstalled = isInstalledModule(renderContext,OPENGRAPH_MODULE);
        JCRNodeWrapper currentNode = renderContext.getMainResource().getNode();
        boolean isEnabled =  currentNode.isNodeType(OPENGRAPH_MIXIN);
        //Disable the filter in case we are in Content Editor preview.
        boolean isCEPreview = renderContext.getRequest().getAttribute("ce_preview") != null;

        if(isInstalled && isEnabled && !isCEPreview){

            //update output to add scripts
            output = enhanceOutput(output, renderContext);
        }

        return output;
    }


    /**
     * This Function is just to add some logic to our filter and therefore not needed to declare a filter
     *
     * @param output    Original output to modify
     * @return          Modified output
     */
    @NotNull
    private String enhanceOutput(String output, RenderContext renderContext) throws Exception{
        Source source = new Source(output);
        OutputDocument outputDocument = new OutputDocument(source);

        //Add webapp script to the HEAD tag
        List<Element> elementList = source.getAllElements(HTMLElementName.HEAD);
        if (elementList != null && !elementList.isEmpty()) {
//            final StartTag headStartTag = elementList.get(0).getStartTag();
//            outputDocument.insert(headStartTag.getEnd(),getHeadScript(renderContext));
            final EndTag headEndTag = elementList.get(0).getEndTag();
            String ogScript = getHeadScript(renderContext);
            outputDocument.insert(headEndTag.getBegin()-1,ogScript);
        }

        output = outputDocument.toString().trim();
        return output;
    }

    private String getHeadScript(RenderContext renderContext) throws RepositoryException, IOException {

        String hostname = getHostname(renderContext);
        String siteName = renderContext.getSite().getName();
        String language =  renderContext.getMainResource().getLocale().getLanguage();
        JCRNodeWrapper currentNode = renderContext.getMainResource().getNode();
        String ogTitle = currentNode.getPropertyAsString("j:ogTitle");
        String ogDescription = currentNode.getPropertyAsString("j:ogDescription");
        String ogType = currentNode.getPropertyAsString("j:ogType");

        StringBuilder headScriptBuilder = new StringBuilder("\n<meta property=\"og:site_name\" content=\""+siteName+"\" />");
        if(ogTitle!=null && !ogTitle.isEmpty())
            headScriptBuilder.append("\n<meta property=\"og:title\" content=\""+ogTitle+"\" />");
        if(ogDescription!=null && !ogDescription.isEmpty())
            headScriptBuilder.append("\n<meta property=\"og:description\" content=\""+ogDescription+"\" />");
        if(ogType!=null && !ogType.isEmpty())
            headScriptBuilder.append("\n<meta property=\"og:type\" content=\""+ogType+"\" />");

        headScriptBuilder.append("\n<meta property=\"og:locale\" content=\""+language+"\" />");
        headScriptBuilder.append("\n<meta property=\"og:url\" content=\""+hostname+currentNode.getUrl()+"\" />");

        try {
            JCRNodeWrapper ogImageNode = (JCRNodeWrapper) currentNode.getProperty("j:ogImage").getNode();
            if(ogImageNode != null){
                String width = ogImageNode.getPropertyAsString("j:width");
                String height = ogImageNode.getPropertyAsString("j:height");
                String alt = ogImageNode.getDisplayableName();
                headScriptBuilder.append("\n<meta property=\"og:image\" content=\""+hostname+ogImageNode.getUrl()+"\" />");
                headScriptBuilder.append("\n<meta property=\"og:image:width\" content=\""+width+"\" />");
                headScriptBuilder.append("\n<meta property=\"og:image:height\" content=\""+height+"\" />");
                headScriptBuilder.append("\n<meta property=\"og:image:alt\" content=\""+alt+"\" />");
            }
        }catch (Exception e){
            logger.info("no image selected in opengraph for content : "+currentNode.getDisplayableName());
        }

        return headScriptBuilder.toString();
    }

    private boolean isInstalledModule(RenderContext renderContext, String moduleName) throws RepositoryException {
        boolean isInstalled = false;
        JCRPropertyWrapper installedModules = renderContext.getSite().getProperty("j:installedModules");
        for (JCRValueWrapper module : installedModules.getValues()) {
            if (moduleName.equals(module.getString())) {
                isInstalled = true;
                break;
            }
        }
        return isInstalled;
    }

    private String getHostname (RenderContext renderContext) {
        String schema = renderContext.getRequest().getScheme();
        String host = renderContext.getRequest().getServerName();
        int port = renderContext.getRequest().getServerPort();
        String hostname = schema+"://"+host;
        if(port!= 80 && port!= 443){
            hostname = hostname + ":" + String.valueOf(port);
        }
        return hostname;
    }
}
