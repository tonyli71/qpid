/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.qpid.server.management.plugin.servlet.rest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.State;

public class RestServlet extends AbstractServlet
{
    private static final Logger LOGGER = Logger.getLogger(RestServlet.class);
    /**
     * An initialization parameter to specify hierarchy
     */
    private static final String HIERARCHY_INIT_PARAMETER = "hierarchy";

    public static final String DEPTH_PARAM = "depth";
    public static final String SORT_PARAM = "sort";

    public static final Set<String> RESERVED_PARAMS = new HashSet<String>(Arrays.asList(DEPTH_PARAM, SORT_PARAM));

    private Class<? extends ConfiguredObject>[] _hierarchy;

    private final ConfiguredObjectToMapConverter _objectConverter = new ConfiguredObjectToMapConverter();
    private final boolean _hierarchyInitializationRequired;

    public RestServlet()
    {
        super();
        _hierarchyInitializationRequired = true;
    }

    public RestServlet(Class<? extends ConfiguredObject>... hierarchy)
    {
        super();
        _hierarchy = hierarchy;
        _hierarchyInitializationRequired = false;
    }

    @Override
    public void init() throws ServletException
    {
        super.init();
        if (_hierarchyInitializationRequired)
        {
            doInitialization();
        }
    }

    @SuppressWarnings("unchecked")
    private void doInitialization() throws ServletException
    {
        ServletConfig config = getServletConfig();
        String hierarchy = config.getInitParameter(HIERARCHY_INIT_PARAMETER);
        if (hierarchy != null && !"".equals(hierarchy))
        {
            List<Class<? extends ConfiguredObject>> classes = new ArrayList<Class<? extends ConfiguredObject>>();
            String[] hierarchyItems = hierarchy.split(",");
            for (String item : hierarchyItems)
            {
                Class<?> itemClass = null;
                try
                {
                    itemClass = Class.forName(item);
                }
                catch (ClassNotFoundException e)
                {
                    try
                    {
                        itemClass = Class.forName("org.apache.qpid.server.model." + item);
                    }
                    catch (ClassNotFoundException e1)
                    {
                        throw new ServletException("Unknown configured object class '" + item
                                + "' is specified in hierarchy for " + config.getServletName());
                    }
                }
                Class<? extends ConfiguredObject> clazz = (Class<? extends ConfiguredObject>)itemClass;
                classes.add(clazz);
            }
            Class<? extends ConfiguredObject>[] hierarchyClasses = (Class<? extends ConfiguredObject>[])new Class[classes.size()];
            _hierarchy = classes.toArray(hierarchyClasses);
        }
        else
        {
            _hierarchy = (Class<? extends ConfiguredObject>[])new Class[0];
        }
    }

    protected Collection<ConfiguredObject<?>> getObjects(HttpServletRequest request)
    {
        String[] pathInfoElements = getPathInfoElements(request);
        List<String> names = new ArrayList<String>();
        if(pathInfoElements != null)
        {
            if(pathInfoElements.length > _hierarchy.length)
            {
                throw new IllegalArgumentException("Too many entries in path for REST servlet "
                        + getServletName() + ". Expected hierarchy length: " + _hierarchy.length
                        + "; Request hierarchy length: " + pathInfoElements.length
                        + "; Path Elements: " + Arrays.toString(pathInfoElements));
            }
            names.addAll(Arrays.asList(pathInfoElements));
        }

        Collection<ConfiguredObject<?>> parents = new ArrayList<ConfiguredObject<?>>();
        parents.add(getBroker());
        Collection<ConfiguredObject<?>> children = new ArrayList<ConfiguredObject<?>>();

        Map<Class<? extends ConfiguredObject>, String> filters =
                new HashMap<Class<? extends ConfiguredObject>, String>();

        for(int i = 0; i < _hierarchy.length; i++)
        {
            if(i == 0 || getBroker().getModel().getChildTypes(_hierarchy[i - 1]).contains(_hierarchy[i]))
            {

                for(ConfiguredObject<?> parent : parents)
                {
                    if(names.size() > i
                            && names.get(i) != null
                            && !names.get(i).equals("*")
                            && names.get(i).trim().length() != 0)
                    {
                        for(ConfiguredObject<?> child : parent.getChildren(_hierarchy[i]))
                        {
                            if(child.getName().equals(names.get(i)))
                            {
                                children.add(child);
                            }
                        }
                    }
                    else
                    {
                        children.addAll((Collection<? extends ConfiguredObject<?>>) parent.getChildren(_hierarchy[i]));
                    }
                }
            }
            else
            {
                children = parents;
                if(names.size() > i
                        && names.get(i) != null
                        && !names.get(i).equals("*")
                        && names.get(i).trim().length() != 0)
                {
                    filters.put(_hierarchy[i], names.get(i));
                }
            }

            parents = children;
            children = new ArrayList<ConfiguredObject<?>>();
        }

        if(!filters.isEmpty())
        {
            Collection<ConfiguredObject<?>> potentials = parents;
            parents = new ArrayList<ConfiguredObject<?>>();

            for(ConfiguredObject o : potentials)
            {

                boolean match = true;

                for(Map.Entry<Class<? extends ConfiguredObject>, String> entry : filters.entrySet())
                {
                    Collection<? extends ConfiguredObject> ancestors =
                            getAncestors(getConfiguredClass(),entry.getKey(), o);
                    match = false;
                    for(ConfiguredObject ancestor : ancestors)
                    {
                        if(ancestor.getName().equals(entry.getValue()))
                        {
                            match = true;
                            break;
                        }
                    }
                    if(!match)
                    {
                        break;
                    }
                }
                if(match)
                {
                    parents.add(o);
                }

            }
        }

        return filter(parents, request);
    }

    private Collection<ConfiguredObject<?>> filter(Collection<ConfiguredObject<?>> objects, HttpServletRequest request)
    {


        Map<String, Collection<String>> filters = new HashMap<String, Collection<String>>();

        for(String param : (Collection<String>) Collections.list(request.getParameterNames()))
        {
            if(!RESERVED_PARAMS.contains(param))
            {
                filters.put(param, Arrays.asList(request.getParameterValues(param)));
            }
        }

        if(filters.isEmpty())
        {
            return objects;
        }

        Collection<ConfiguredObject<?>> filteredObj = new ArrayList<ConfiguredObject<?>>(objects);

        Iterator<ConfiguredObject<?>> iter = filteredObj.iterator();

        while(iter.hasNext())
        {
            ConfiguredObject obj = iter.next();
            for(Map.Entry<String, Collection<String>> entry : filters.entrySet())
            {
                Object value = obj.getAttribute(entry.getKey());
                if(!entry.getValue().contains(String.valueOf(value)))
                {
                    iter.remove();
                }
            }

        }

        return filteredObj;
    }

    private Collection<? extends ConfiguredObject> getAncestors(Class<? extends ConfiguredObject> childType,
                                                                Class<? extends ConfiguredObject> ancestorType,
                                                                ConfiguredObject child)
    {
        Collection<ConfiguredObject> ancestors = new HashSet<ConfiguredObject>();
        Collection<Class<? extends ConfiguredObject>> parentTypes = child.getModel().getParentTypes(childType);

        for(Class<? extends ConfiguredObject> parentClazz : parentTypes)
        {
            if(parentClazz == ancestorType)
            {
                ConfiguredObject parent = child.getParent(parentClazz);
                if(parent != null)
                {
                    ancestors.add(parent);
                }
            }
            else
            {
                ConfiguredObject parent = child.getParent(parentClazz);
                if(parent != null)
                {
                    ancestors.addAll(getAncestors(parentClazz, ancestorType, parent));
                }
            }
        }

        return ancestors;
    }

    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        setCachingHeadersOnResponse(response);

        Collection<ConfiguredObject<?>> allObjects = getObjects(request);

        // TODO - sort special params, everything else should act as a filter
        int depth = getDepthParameterFromRequest(request);

        List<Map<String, Object>> output = new ArrayList<Map<String, Object>>();
        for(ConfiguredObject configuredObject : allObjects)
        {
            output.add(_objectConverter.convertObjectToMap(configuredObject, getConfiguredClass(),
                    depth));
        }

        final Writer writer = new BufferedWriter(response.getWriter());
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.writeValue(writer, output);

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private Class<? extends ConfiguredObject> getConfiguredClass()
    {
        return _hierarchy.length == 0 ? Broker.class : _hierarchy[_hierarchy.length-1];
    }

    @Override
    protected void doPutWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("application/json");

        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String,Object> providedObject = mapper.readValue(request.getInputStream(), LinkedHashMap.class);


        List<String> names = new ArrayList<String>();
        String[] pathInfoElements = getPathInfoElements(request);
        if(pathInfoElements != null )
        {
            if(pathInfoElements.length != _hierarchy.length)
            {
                throw new IllegalArgumentException("Path to object to create must be fully specified. "
                       + "Found " + names + " of size " + names.size() + " expecting " + _hierarchy.length);
            }
            names.addAll(Arrays.asList(pathInfoElements));
        }

        if (names.isEmpty())
        {
            if (_hierarchy.length == 0)
            {
                try
                {
                    doUpdate(getBroker(), providedObject);
                    response.setStatus(HttpServletResponse.SC_OK);
                }
                catch(RuntimeException e)
                {
                    setResponseStatus(response, e);
                }
                return;
            }
            else
            {
                throw new ServletException("Cannot identify request target object");
            }
        }

        providedObject.put("name", names.get(names.size()-1));

        @SuppressWarnings("unchecked")
        Collection<ConfiguredObject>[] objects = new Collection[_hierarchy.length];
        if(_hierarchy.length == 1)
        {
            createOrUpdate(providedObject, _hierarchy[0], getBroker(), null, response);
        }
        else
        {
            for(int i = 0; i < _hierarchy.length-1; i++)
            {
                objects[i] = new HashSet<ConfiguredObject>();
                if(i == 0)
                {
                    for(ConfiguredObject object : getBroker().getChildren(_hierarchy[0]))
                    {
                        if(object.getName().equals(names.get(0)))
                        {
                            objects[0].add(object);
                            break;
                        }
                    }
                }
                else
                {
                    for(int j = i-1; j >=0; j--)
                    {
                        if(getBroker().getModel().getChildTypes(_hierarchy[j]).contains(_hierarchy[i]))
                        {
                            for(ConfiguredObject<?> parent : objects[j])
                            {
                                for(ConfiguredObject<?> object : parent.getChildren(_hierarchy[i]))
                                {
                                    if(object.getName().equals(names.get(i)))
                                    {
                                        objects[i].add(object);
                                    }
                                }
                            }
                            break;
                        }
                    }
                }

            }
            List<ConfiguredObject> parents = new ArrayList<ConfiguredObject>();
            Class<? extends ConfiguredObject> objClass = getConfiguredClass();
            Collection<Class<? extends ConfiguredObject>> parentClasses = getBroker().getModel().getParentTypes(objClass);
            for(int i = _hierarchy.length-2; i >=0 ; i--)
            {
                if(parentClasses.contains(_hierarchy[i]))
                {
                    if(objects[i].size() == 1)
                    {
                        parents.add(objects[i].iterator().next());
                    }
                    else
                    {
                        throw new IllegalArgumentException("Cannot deduce parent of class "
                                + _hierarchy[i].getSimpleName());
                    }
                }

            }
            ConfiguredObject theParent = parents.remove(0);
            ConfiguredObject[] otherParents = parents.toArray(new ConfiguredObject[parents.size()]);

            createOrUpdate(providedObject, objClass, theParent, otherParents, response);
        }
    }

    private void createOrUpdate(Map<String, Object> providedObject, Class<? extends ConfiguredObject> objClass,
            ConfiguredObject theParent, ConfiguredObject[] otherParents, HttpServletResponse response) throws IOException
    {
        try
        {
            Collection<? extends ConfiguredObject> existingChildren = theParent.getChildren(objClass);
            for(ConfiguredObject obj: existingChildren)
            {
                if((providedObject.containsKey("id") && String.valueOf(providedObject.get("id")).equals(obj.getId().toString()))
                   || (obj.getName().equals(providedObject.get("name")) && equalParents(obj, otherParents, objClass)))
                {
                    doUpdate(obj, providedObject);
                    response.setStatus(HttpServletResponse.SC_OK);
                    return;
                }
            }

            theParent.createChild(objClass, providedObject, otherParents);
            response.setStatus(HttpServletResponse.SC_CREATED);
        }
        catch (RuntimeException e)
        {
            setResponseStatus(response, e);
        }
    }

    private void doUpdate(ConfiguredObject obj, Map<String, Object> providedObject)
    {
        obj.setAttributes(providedObject);
    }

    private boolean equalParents(ConfiguredObject obj, ConfiguredObject[] otherParents, Class<? extends ConfiguredObject> objClass)
    {
        if(otherParents == null || otherParents.length == 0)
        {
            return true;
        }

        Collection<Class<? extends ConfiguredObject>> parentClasses = obj.getModel().getParentTypes(objClass);

        for (ConfiguredObject parent : otherParents)
        {
            boolean found = false;
            for (Class<? extends ConfiguredObject> parentClass : parentClasses)
            {
                if (parent == obj.getParent(parentClass))
                {
                    found = true;
                    break;
                }
            }

            if (!found)
            {
                return false;
            }
        }

        return true;
    }

    private void setResponseStatus(HttpServletResponse response, RuntimeException e) throws IOException
    {
        if (e instanceof AccessControlException)
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Caught security exception, sending " + HttpServletResponse.SC_FORBIDDEN, e);
            }
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
        else
        {
            LOGGER.warn("Caught exception", e);

            // TODO
            response.setStatus(HttpServletResponse.SC_CONFLICT);
        }
    }

    @Override
    protected void doDeleteWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        setCachingHeadersOnResponse(response);
        try
        {
            Collection<ConfiguredObject<?>> allObjects = getObjects(request);
            for(ConfiguredObject o : allObjects)
            {
                o.setDesiredState(o.getState(), State.DELETED);
            }

            response.setStatus(HttpServletResponse.SC_OK);
        }
        catch(RuntimeException e)
        {
            setResponseStatus(response, e);
        }
    }

    private void setCachingHeadersOnResponse(HttpServletResponse response)
    {
        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader ("Expires", 0);
    }

    private int getDepthParameterFromRequest(HttpServletRequest request)
    {
        int depth = 1;
        final String depthString = request.getParameter(DEPTH_PARAM);
        if(depthString!=null)
        {
            try
            {
                depth = Integer.parseInt(depthString);
            }
            catch (NumberFormatException e)
            {
                LOGGER.warn("Could not parse " + depthString + " as integer");
            }
        }
        return depth;
    }


}
