/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2004-2024 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.tool.assessment.ui.listener.evaluation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.faces.context.FacesContext;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.ActionEvent;
import javax.faces.event.ActionListener;

import lombok.extern.slf4j.Slf4j;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.rubrics.api.RubricsService;
import org.sakaiproject.rubrics.api.model.ToolItemRubricAssociation;
import org.sakaiproject.tool.assessment.facade.AgentFacade;
import org.sakaiproject.tool.assessment.ui.bean.evaluation.AgentResults;
import org.sakaiproject.tool.assessment.ui.bean.evaluation.QuestionScoresBean;
import org.sakaiproject.tool.assessment.ui.bean.evaluation.TotalScoresBean;
import org.sakaiproject.tool.assessment.ui.listener.util.ContextUtil;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.rubrics.api.repository.AssociationRepository;
import javax.servlet.http.HttpServletResponse;
import org.sakaiproject.util.ResourceLoader;

@Slf4j
 public class ExportRubrics
  implements ActionListener
{
  private final RubricsService rubricsService = ComponentManager.get(RubricsService.class);
  private final UserDirectoryService userDirectoryService = ComponentManager.get(UserDirectoryService.class);
  private AssociationRepository associationRepository = ComponentManager.get(AssociationRepository.class);
  private static final ResourceLoader rb = new ResourceLoader("org.sakaiproject.tool.assessment.bundle.EvaluationMessages");

  /**
   * Standard process action method.
   * @param ae ActionEvent
   * @throws AbortProcessingException
   */
  public void processAction(ActionEvent ae) throws AbortProcessingException {
    File tempFile;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      ZipOutputStream out = new ZipOutputStream(baos);

      QuestionScoresBean bean = (QuestionScoresBean) ContextUtil.lookupBean("questionScores");
      TotalScoresBean tBean = (TotalScoresBean) ContextUtil.lookupBean("totalScores");
      String templateFilename = rb.getString("question") + "_" + bean.getItemName();
      String toolId = "sakai.samigo";
      List<AgentResults> agents = (List<AgentResults>) bean.getAgents();
      for(AgentResults ar : agents) {
        String itemId = "pub." + bean.getPublishedId() + "." + ContextUtil.lookupParam("itemId");
        User user = userDirectoryService.getUserByEid(ar.getAgentEid());

        Optional<ToolItemRubricAssociation> optAssociation = associationRepository.findByToolIdAndItemId(toolId, itemId);
        long rubricId = optAssociation.isPresent()? optAssociation.get().getRubric().getId() : 0l;

        String evaluatedItemId = rubricsService.getRubricEvaluationObjectId(itemId, user.getId(), toolId, AgentFacade.getCurrentSiteId());
        byte[] pdf = rubricsService.createPdf(AgentFacade.getCurrentSiteId(), rubricId, "sakai.samigo", itemId, evaluatedItemId);
        final ZipEntry zipEntryPdf = new ZipEntry(user.getEid() + "_" + templateFilename + ".pdf");

        out.putNextEntry(zipEntryPdf);
        out.write(pdf);
        out.closeEntry();
      }
      out.finish();
      out.close();

      FacesContext faces = FacesContext.getCurrentInstance();
      
      HttpServletResponse response = (HttpServletResponse)faces.getExternalContext().getResponse();
      response.reset();	// Eliminate the added-on stuff
      String fileName = tBean.getAssessmentName().replaceAll(" ", "_") + "_" + templateFilename;
      response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "_" + rb.getString("rubrics") + ".zip\"");
      response.setContentType("application/zip");
      response.setContentLength(baos.size());

      baos.writeTo(response.getOutputStream());
      faces.responseComplete();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
  }

}
