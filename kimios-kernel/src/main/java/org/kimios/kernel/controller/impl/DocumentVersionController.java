/*
 * Kimios - Document Management System Software
 * Copyright (C) 2012-2013  DevLib'
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.kimios.kernel.controller.impl;

import java.util.Date;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilderFactory;

import org.kimios.exceptions.ConfigException;
import org.kimios.kernel.controller.AKimiosController;
import org.kimios.kernel.controller.IDocumentVersionController;
import org.kimios.kernel.dms.Document;
import org.kimios.kernel.dms.DocumentComment;
import org.kimios.kernel.dms.DocumentFactory;
import org.kimios.kernel.dms.DocumentType;
import org.kimios.kernel.dms.DocumentTypeFactory;
import org.kimios.kernel.dms.DocumentVersion;
import org.kimios.kernel.dms.FactoryInstantiator;
import org.kimios.kernel.dms.Meta;
import org.kimios.kernel.dms.MetaBooleanValue;
import org.kimios.kernel.dms.MetaDateValue;
import org.kimios.kernel.dms.MetaFactory;
import org.kimios.kernel.dms.MetaNumberValue;
import org.kimios.kernel.dms.MetaStringValue;
import org.kimios.kernel.dms.MetaType;
import org.kimios.kernel.dms.MetaValue;
import org.kimios.kernel.dms.MetaValueFactory;
import org.kimios.kernel.events.EventContext;
import org.kimios.kernel.exception.AccessDeniedException;
import org.kimios.kernel.exception.CheckoutViolationException;
import org.kimios.kernel.exception.DataSourceException;
import org.kimios.kernel.exception.RepositoryException;
import org.kimios.kernel.exception.XMLException;
import org.kimios.kernel.repositories.RepositoryManager;
import org.kimios.kernel.security.Session;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;

/**
 * @author Fabien Alin
 */
public class DocumentVersionController extends AKimiosController implements IDocumentVersionController
{
    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#getDocumentVersion(org.kimios.kernel.security.Session, long)
    */
    public DocumentVersion getDocumentVersion(Session session, long documentVersionUid) throws ConfigException,
            DataSourceException, AccessDeniedException
    {
        DocumentVersion dv = dmsFactoryInstantiator.getDocumentVersionFactory().getDocumentVersion(documentVersionUid);
        if (getSecurityAgent()
                .isReadable(dv.getDocument(), session.getUserName(), session.getUserSource(), session.getGroups()))
        {
            return dv;
        } else {
            throw new AccessDeniedException();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#createDocumentVersion(org.kimios.kernel.security.Session, long)
    */
    public long createDocumentVersion(Session session, long documentUid)
            throws CheckoutViolationException, ConfigException, DataSourceException, AccessDeniedException
    {
        DocumentFactory docFactory = dmsFactoryInstantiator.getDocumentFactory();
        Document d = docFactory.getDocument(documentUid);
        DocumentVersion dv =
                new DocumentVersion(-1, session.getUserName(), session.getUserSource(), new Date(), new Date(),
                        documentUid, 0, null);
        if (getSecurityAgent().isWritable(d, session.getUserName(), session.getUserSource(), session.getGroups())) {
            dmsFactoryInstantiator.getDocumentVersionFactory().saveDocumentVersion(dv);
            return dv.getUid();
        } else {
            throw new AccessDeniedException();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#createDocumentVersionFromLatest(org.kimios.kernel.security.Session, long)
    */
    public long createDocumentVersionFromLatest(Session session, long documentUid)
            throws CheckoutViolationException, ConfigException, DataSourceException, AccessDeniedException,
            RepositoryException
    {
        Document doc = dmsFactoryInstantiator.getDocumentFactory().getDocument(documentUid);
        DocumentVersion dv = this.getLastDocumentVersion(session, documentUid);
        DocumentVersion newVersion =
                new DocumentVersion(-1, session.getUserName(), session.getUserSource(), new Date(), new Date(),
                        doc.getUid(), dv.getLength(), dv.getDocumentType());
        newVersion.setHashMD5(dv.getHashMD5());
        newVersion.setHashSHA1(dv.getHashSHA1());
        /*
            The new store path will be set on version save (@see HDocumentVersionFactory)
         */
        if (getSecurityAgent().isWritable(doc, session.getUserName(), session.getUserSource(), session.getGroups())) {
            dmsFactoryInstantiator.getDocumentVersionFactory().saveDocumentVersion(newVersion);
            /*
                Copy old version
             */
            RepositoryManager.copyVersion(dv, newVersion);
            //Copying metas values
            List<MetaValue> vMetas = dmsFactoryInstantiator.getMetaValueFactory().getMetaValues(dv);
            Vector<MetaValue> toSave = new Vector<MetaValue>();
            for (MetaValue m : vMetas) {
                switch (m.getMeta().getMetaType()) {
                    case MetaType.STRING:
                        toSave.add(new MetaStringValue(newVersion, m.getMeta(),
                                (m.getValue() != null ? (String) m.getValue() : "")));
                        break;
                    case MetaType.NUMBER:
                        toSave.add(new MetaNumberValue(newVersion, m.getMeta(),
                                (m.getValue() != null ? (Double) m.getValue() : -1)));
                        break;

                    case MetaType.DATE:
                        toSave.add(new MetaDateValue(newVersion, m.getMeta(),
                                (m.getValue() != null ? (Date) m.getValue() : null)));
                        break;

                    case MetaType.BOOLEAN:
                        toSave.add(new MetaBooleanValue(newVersion, m.getMeta(),
                                (m.getValue() != null ? (Boolean) m.getValue() : null)));
                        break;
                }
            }
            MetaValueFactory mvf = dmsFactoryInstantiator.getMetaValueFactory();
            for (MetaValue b : toSave) {
                mvf.saveMetaValue(b);
            }
            return newVersion.getUid();
        } else {
            throw new AccessDeniedException();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#updateDocumentVersion(org.kimios.kernel.security.Session, long, long)
    */
    public void updateDocumentVersion(Session session, long documentUid, long documentTypeUid, String xmlStream) throws
            XMLException, CheckoutViolationException, ConfigException, DataSourceException, AccessDeniedException
    {
        DocumentTypeFactory typeFactory = dmsFactoryInstantiator.getDocumentTypeFactory();
        Document d = dmsFactoryInstantiator.getDocumentFactory().getDocument(documentUid);
        DocumentVersion dv = dmsFactoryInstantiator.getDocumentVersionFactory().getLastDocumentVersion(d);
        if (getSecurityAgent().isWritable(d, session.getUserName(), session.getUserSource(), session.getGroups())) {
            //Getting existing values
            List<MetaValue> vMetaValuesExisting = dmsFactoryInstantiator.getMetaValueFactory().getMetaValues(dv);
            //Changing document version to new type
            DocumentType newDt = typeFactory.getDocumentType(documentTypeUid);
            dv.setDocumentType(newDt);
            dmsFactoryInstantiator.getDocumentVersionFactory().updateDocumentVersion(dv);

            if (xmlStream == null || xmlStream.length() == 0) {
                //Metas list of new DocumentType
                // keep existing value for inheritance
                Vector<Meta> vMetaNewType = null;
                if (dv.getDocumentType() != null) {
                    vMetaNewType = dmsFactoryInstantiator.getMetaFactory().getMetas(newDt);
                } else {
                    vMetaNewType = new Vector<Meta>();
                }

                Vector<MetaValue> toDelete = new Vector<MetaValue>();
                for (MetaValue v : vMetaValuesExisting) {
                    if (!vMetaNewType.contains(v.getMeta())) {
                        toDelete.add(v);
                    }
                }
                for (MetaValue v : toDelete) {
                    dmsFactoryInstantiator.getMetaValueFactory().deleteMetaValue(v);
                }
            } else {
                //set all value
                MetaValueFactory fact = dmsFactoryInstantiator.getMetaValueFactory();
                Vector<MetaValue> vNewMetas = getMetaValuesFromXML(xmlStream, dv.getUid());
                List<MetaValue> vMetas = fact.getMetaValues(dv);
                for (MetaValue m : vMetas) {
                    fact.deleteMetaValue(m);
                }
                for (MetaValue m : vNewMetas) {
                    fact.saveMetaValue(m);
                }
            }

            dv.setModificationDate(new Date());
            dmsFactoryInstantiator.getDocumentVersionFactory().updateDocumentVersion(dv);
            EventContext.addParameter("version", dv);
            EventContext.addParameter("documentTypeSet", newDt);
        } else {
            throw new AccessDeniedException();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#deleteDocumentVersion(org.kimios.kernel.security.Session, long)
    */
    public void deleteDocumentVersion(long documentVersionUid)
            throws CheckoutViolationException, ConfigException, DataSourceException, AccessDeniedException
    {
        DocumentVersion dv = dmsFactoryInstantiator.getDocumentVersionFactory().getDocumentVersion(documentVersionUid);
        dmsFactoryInstantiator.getDocumentVersionFactory().deleteDocumentVersion(dv);
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#getMetaValue(org.kimios.kernel.security.Session, long, long)
    */
    public Object getMetaValue(Session session, long documentVersionUid, long metaUid)
            throws ConfigException, DataSourceException, AccessDeniedException
    {
        DocumentVersion dv = dmsFactoryInstantiator.getDocumentVersionFactory().getDocumentVersion(documentVersionUid);
        if (getSecurityAgent()
                .isReadable(dv.getDocument(), session.getUserName(), session.getUserSource(), session.getGroups()))
        {
            Meta m = dmsFactoryInstantiator.getMetaFactory().getMeta(metaUid);
            MetaValue mv = dmsFactoryInstantiator.getMetaValueFactory().getMetaValue(dv, m);
            if (mv == null) {
                return mv;
            } else {
                return mv.getValue();
            }
        } else {
            throw new AccessDeniedException();
        }
    }

    /**
     * Convenience method to generate meta list from an xml descriptor
     */
    private Vector<MetaValue> getMetaValuesFromXML(String xmlStream, long uid)
            throws XMLException, DataSourceException, ConfigException
    {
        DocumentVersion dv = dmsFactoryInstantiator.getDocumentVersionFactory().getDocumentVersion(uid);
        Vector<MetaValue> v = new Vector<MetaValue>();
        if (dv != null) {
            try {
                org.w3c.dom.Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(new java.io.ByteArrayInputStream(xmlStream.getBytes()));
                org.w3c.dom.Element root = doc.getDocumentElement();
                NodeList list = root.getChildNodes();
                for (int i = 0; i < list.getLength(); i++) {
                    if (list.item(i).getNodeName().equalsIgnoreCase("meta")) {
                        long metaUid =
                                Long.parseLong(list.item(i).getAttributes().getNamedItem("uid").getTextContent());
                        Meta m = dmsFactoryInstantiator.getMetaFactory().getMeta(metaUid);
                        LoggerFactory.getLogger( DocumentVersionController.class )
                            .info( "Parsed VALUE " + list.item( i ).getTextContent() );
                        MetaValue mv = toMetaValue(m.getMetaType(), dv, m, list.item(i).getTextContent());
                        if (mv != null) {
                            v.add(mv);
                        }
                    }
                }
            } catch (Exception e) {
                throw new XMLException();
            }
        }
        return v;
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#toMetaValue(int, org.kimios.kernel.dms.DocumentVersion, long, java.lang.String)
    */
    public MetaValue toMetaValue(int metaType, DocumentVersion version, Meta meta, String metaValue)
    {
        MetaValue metaV = null;
        switch (metaType) {
            case MetaType.BOOLEAN:
                metaV = new MetaBooleanValue(
                        version,
                        meta,
                        Boolean.parseBoolean(metaValue));
                break;
            case MetaType.DATE:
                if (Long.parseLong(metaValue) != -1) {
                    metaV = new MetaDateValue(
                            version,
                            meta,
                            new Date(Long.parseLong(metaValue)));
                }

                break;
            case MetaType.NUMBER:
                metaV = new MetaNumberValue(
                        version,
                        meta,
                        Double.parseDouble(metaValue));
                break;
            case MetaType.STRING:
                metaV = new MetaStringValue(
                        version,
                        meta,
                        metaValue);
                break;
        }
        return metaV;
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#updateMetasValue(org.kimios.kernel.security.Session, long, java.lang.String)
    */
    public void updateMetasValue(Session session, long uid, String xmlStream)
            throws XMLException, AccessDeniedException, ConfigException, DataSourceException
    {
        DocumentVersion dv = dmsFactoryInstantiator.getDocumentVersionFactory().getDocumentVersion(uid);
        if (getSecurityAgent()
                .isWritable(dv.getDocument(), session.getUserName(), session.getUserSource(), session.getGroups()))
        {
            MetaValueFactory fact = dmsFactoryInstantiator.getMetaValueFactory();
            Vector<MetaValue> vNewMetas = getMetaValuesFromXML(xmlStream, uid);
            List<MetaValue> vMetas = fact.getMetaValues(dv);
            for (MetaValue m : vMetas) {
                fact.deleteMetaValue(m);
            }
            for (MetaValue m : vNewMetas) {
                fact.saveMetaValue(m);
            }
            dv.setModificationDate(new Date());
            dmsFactoryInstantiator.getDocumentVersionFactory().updateDocumentVersion(dv);
        } else {
            throw new AccessDeniedException();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#getDocumentVersions(org.kimios.kernel.security.Session, long)
    */
    public Vector<DocumentVersion> getDocumentVersions(Session session, long documentUid)
            throws ConfigException, DataSourceException
    {
        DocumentFactory docFactory = dmsFactoryInstantiator.getDocumentFactory();
        Document d = docFactory.getDocument(documentUid);
        Vector<DocumentVersion> vVersions = new Vector<DocumentVersion>();
        if (getSecurityAgent().isReadable(d, session.getUserName(), session.getUserSource(), session.getGroups())) {
            vVersions = dmsFactoryInstantiator.getDocumentVersionFactory().getVersions(d);
        }
        return vVersions;
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#getLastDocumentVersion(org.kimios.kernel.security.Session, long)
    */
    public DocumentVersion getLastDocumentVersion(Session session, long documentUid)
            throws AccessDeniedException, ConfigException, DataSourceException
    {
        Document d = dmsFactoryInstantiator.getDocumentFactory().getDocument(documentUid);
        if (getSecurityAgent().isReadable(d, session.getUserName(), session.getUserSource(), session.getGroups())) {
            DocumentVersion dv = dmsFactoryInstantiator.getDocumentVersionFactory().getLastDocumentVersion(d);
            return dv;
        } else {
            throw new AccessDeniedException();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#getDocumentComment(org.kimios.kernel.security.Session, long)
    */
    public DocumentComment getDocumentComment(Session session, long uid)
            throws AccessDeniedException, ConfigException, DataSourceException
    {
        DocumentComment comment = dmsFactoryInstantiator.getDocumentCommentFactory().getDocumentComment(uid);
        Document d = comment.getDocumentVersion().getDocument();
        if (getSecurityAgent().isReadable(d, session.getUserName(), session.getUserSource(), session.getGroups())) {
            return comment;
        } else {
            throw new AccessDeniedException();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#getDocumentComments(org.kimios.kernel.security.Session, long)
    */
    public Vector<DocumentComment> getDocumentComments(Session session, long documentVersionUid)
            throws AccessDeniedException, ConfigException, DataSourceException
    {
        DocumentVersion dv = dmsFactoryInstantiator.getDocumentVersionFactory().getDocumentVersion(documentVersionUid);
        if (getSecurityAgent()
                .isReadable(dv.getDocument(), session.getUserName(), session.getUserSource(), session.getGroups()))
        {
            return dmsFactoryInstantiator.getDocumentCommentFactory().getDocumentComments(documentVersionUid);
        } else {
            throw new AccessDeniedException();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#createDocumentComment(org.kimios.kernel.security.Session, long, java.lang.String)
    */
    public long createDocumentComment(Session session, long documentVersionUid, String comment)
            throws AccessDeniedException, ConfigException, DataSourceException
    {
        DocumentVersion dv = dmsFactoryInstantiator.getDocumentVersionFactory().getDocumentVersion(documentVersionUid);
        if (getSecurityAgent()
                .isWritable(dv.getDocument(), session.getUserName(), session.getUserSource(), session.getGroups()))
        {
            DocumentComment dc =
                    new DocumentComment(-1, documentVersionUid, session.getUserName(), session.getUserSource(), comment,
                            new Date());
            long idComment = dmsFactoryInstantiator.getDocumentCommentFactory().saveDocumentComment(dc);
            return idComment;
        } else {
            throw new AccessDeniedException();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#updateDocumentComment(org.kimios.kernel.security.Session, long, long, java.lang.String)
    */
    public void updateDocumentComment(Session session, long documentVersionUid, long commentUid, String newComment)
            throws AccessDeniedException, ConfigException, DataSourceException
    {
        DocumentVersion dv = dmsFactoryInstantiator.getDocumentVersionFactory().getDocumentVersion(documentVersionUid);
        DocumentComment dc = dmsFactoryInstantiator.getDocumentCommentFactory().getDocumentComment(commentUid);
        if (getSecurityAgent()
                .isWritable(dv.getDocument(), session.getUserName(), session.getUserSource(), session.getGroups()) &&
                session.getUserName().equals(dc.getAuthorName()) &&
                session.getUserSource().equals(dc.getAuthorSource()) ||
                getSecurityAgent().isAdmin(session.getUserName(), session.getUserSource()))
        {
            dc.setDate(new Date());
            dc.setComment(newComment);
            dmsFactoryInstantiator.getDocumentCommentFactory().updateDocumentComment(dc);
        } else {
            throw new AccessDeniedException();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#deleteDocumentComment(org.kimios.kernel.security.Session, long)
    */
    public void deleteDocumentComment(Session session, long commentUid)
            throws AccessDeniedException, ConfigException, DataSourceException
    {
        DocumentComment comment = dmsFactoryInstantiator.getDocumentCommentFactory().getDocumentComment(commentUid);
        DocumentVersion dv =
                dmsFactoryInstantiator.getDocumentVersionFactory().getDocumentVersion(comment.getDocumentVersionUid());
        if (getSecurityAgent()
                .isWritable(dv.getDocument(), session.getUserName(), session.getUserSource(), session.getGroups()) &&
                session.getUserName().equals(comment.getAuthorName()) &&
                session.getUserSource().equals(comment.getAuthorSource()) ||
                getSecurityAgent().isAdmin(session.getUserName(), session.getUserSource()))
        {
            dmsFactoryInstantiator.getDocumentCommentFactory().deleteDocumentComment(comment);
        } else {
            throw new AccessDeniedException();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#getMetas(org.kimios.kernel.security.Session, long)
    */
    public Vector<Meta> getMetas(Session session, long documentTypeUid)
            throws AccessDeniedException, ConfigException, DataSourceException
    {
        MetaFactory mf = dmsFactoryInstantiator.getMetaFactory();
        DocumentType dt = dmsFactoryInstantiator.getDocumentTypeFactory().getDocumentType(documentTypeUid);
        if (dt != null) {
            return mf.getMetas(dt);
        } else {
            return new Vector<Meta>();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#getMeta(org.kimios.kernel.security.Session, long)
    */
    public Meta getMeta(Session session, long metaUid)
            throws AccessDeniedException, ConfigException, DataSourceException
    {
        Meta m = dmsFactoryInstantiator.getMetaFactory().getMeta(metaUid);
        return m;
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#getUnheritedMetas(org.kimios.kernel.security.Session, long)
    */
    public Vector<Meta> getUnheritedMetas(Session session, long documentTypeUid)
            throws AccessDeniedException, ConfigException, DataSourceException
    {
        MetaFactory mf = dmsFactoryInstantiator.getMetaFactory();
        DocumentType dt = dmsFactoryInstantiator.getDocumentTypeFactory().getDocumentType(documentTypeUid);
        if (dt != null) {
            return mf.getUnheritedMetas(dt);
        } else {
            return new Vector<Meta>();
        }
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#getDocumentVersion(org.kimios.kernel.security.Session, java.lang.String, java.lang.String)
    */
    @Deprecated
    public DocumentVersion getDocumentVersion(Session session, String hashMD5, String hashSHA)
            throws AccessDeniedException, ConfigException, DataSourceException
    {
        return null;
    }

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.IDocumentVersionController#getDocumentTypeByName(org.kimios.kernel.security.Session, java.lang.String)
    */
    public DocumentType getDocumentTypeByName(Session session, String name)
            throws AccessDeniedException, ConfigException, DataSourceException
    {
        DocumentTypeFactory dtf = dmsFactoryInstantiator.getDocumentTypeFactory();
        return dtf.getDocumentTypeByName(name);
    }

    public void updateDocumentVersionInformation(Session session, long documentVersionUid)
            throws ConfigException, DataSourceException, AccessDeniedException,
            RepositoryException
    {
        DocumentVersion dv = dmsFactoryInstantiator.getDocumentVersionFactory().getDocumentVersion(documentVersionUid);
        if (getSecurityAgent()
                .isReadable(dv.getDocument(), session.getUserName(), session.getUserSource(), session.getGroups()))
        {
            dv.updateVersionInformation();
        } else {
            throw new AccessDeniedException();
        }
    }

    public List<MetaValue> getMetaValues(Session session, long documentVersionId)
            throws ConfigException, DataSourceException, AccessDeniedException
    {
        DocumentVersion dv = dmsFactoryInstantiator.getDocumentVersionFactory().getDocumentVersion(documentVersionId);
        if (getSecurityAgent()
                .isReadable(dv.getDocument(), session.getUserName(), session.getUserSource(), session.getGroups()))
        {
            List<MetaValue> items = dmsFactoryInstantiator.getMetaValueFactory().getMetaValues(dv);
            return items;
        } else {
            throw new AccessDeniedException();
        }
    }

    public List<DocumentVersion> getOprhansDocumentVersion()
    {
        return FactoryInstantiator.getInstance().getDocumentVersionFactory().getVersionsToDelete();
    }
}

