/******************************************************************************
 * Copyright 2015 sakaiproject.org Licensed under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package org.sakaiproject.webapi.controllers;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.assignment.api.AssignmentServiceConstants;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.grading.api.Assignment;
import org.sakaiproject.grading.api.GradeDefinition;
import org.sakaiproject.grading.api.GradingConstants;
import org.sakaiproject.grading.api.SortType;
import org.sakaiproject.portal.api.PortalService;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.webapi.beans.GradebookItemRestBean;
import org.sakaiproject.webapi.beans.GradebookRestBean;
import org.sakaiproject.webapi.beans.GradeRestBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 */
@Slf4j
@RestController
public class GradesController extends AbstractSakaiApiController {

    @Resource
    private EntityManager entityManager;

    @Resource(name = "org.sakaiproject.grading.api.GradingService")
    private org.sakaiproject.grading.api.GradingService gradingService;

    @Resource
    private PortalService portalService;

    @Resource
    private SiteService siteService;

    private final Function<String, List<GradeRestBean>> gradeDataSupplierForSite = (siteId) -> {
        System.out.println("1");
        List<Assignment> assignments = gradingService.getViewableAssignmentsForCurrentUser(siteId, siteId, SortType.SORT_BY_NONE);
        List<Long> assignmentIds = assignments.stream().map(Assignment::getId).collect(Collectors.toList());
        System.out.println("2");

        // no need to continue if the site doesn't have gradebook items
        if (assignmentIds.isEmpty()) return Collections.emptyList();
        System.out.println("3");

        // collect site information
        return siteService.getOptionalSite(siteId).map(site -> {
            String userId = checkSakaiSession().getUserId();
            Role role = site.getUserRole(userId);
            boolean isMaintainer = StringUtils.equalsIgnoreCase(site.getMaintainRole(), role.getId());

            List<String> userIds = isMaintainer
                    ? site.getRoles().stream()
                            .map(Role::getId)
                            .filter(r -> !site.getMaintainRole().equals(r))
                            .flatMap(r -> site.getUsersHasRole(r).stream())
                            .collect(Collectors.toList())
                    : List.of(userId);

            Map<Long, List<GradeDefinition>> gradeDefinitions = gradingService.getGradesWithoutCommentsForStudentsForItems(siteId, siteId, assignmentIds, userIds);
            System.out.println("4");

            List<GradeRestBean> beans = new ArrayList<>();
            // collect information for each gradebook item
            for (Assignment a : assignments) {
                System.out.println("5");
                GradeRestBean bean = new GradeRestBean(a);
                bean.setSiteTitle(site.getTitle());
                bean.setSiteRole(role.getId());

                // collect information for internal gb item
                List<GradeDefinition> gd = gradeDefinitions.get(a.getId());
                System.out.println("6");

                if (gd == null) {
                    // no grades for this gb assignment yet
                    bean.setScore("");
                    System.out.println("7");
                    if (isMaintainer) {
                        bean.setUngraded(userIds.size());
                    }
                    bean.setNotGradedYet(true);
                } else {
                    System.out.println("8");
                    if (isMaintainer) {
                        double total = 0;
                        for (GradeDefinition d : gd) {
                            if (Objects.equals(GradingConstants.GRADE_TYPE_POINTS, d.getGradeEntryType())) {
                                String grade = d.getGrade();
                                if (!StringUtils.isBlank(grade)) {
                                    total += Double.parseDouble(grade);
                                    System.out.println("9");
                                }
                            }
                        }
                        bean.setScore(total > 0 ? String.format("%.2f", total / gd.size()) : "");
                        bean.setUngraded(userIds.size() - gd.size());
                        bean.setNotGradedYet(gd.isEmpty());
                        System.out.println("10");
                    } else {
                        System.out.println("11");
                        if (a.getReleased() && !gd.isEmpty()) {
                            bean.setNotGradedYet(false);
                            bean.setScore(StringUtils.trimToEmpty(gd.get(0).getGrade()));
                        } else {
                            bean.setScore("");
                            bean.setNotGradedYet(true);
                        }
                        System.out.println("12");
                    }
                }

                System.out.println("13");
                String url = "";
                if (a.getExternallyMaintained()) {
                    url = entityManager.getUrl(a.getReference(), Entity.UrlType.PORTAL).orElse("");
                }
                System.out.println("14");
                if (StringUtils.isBlank(url)) {
// TODO S2U-26 COMPROBAR LOS GB A LOS QUE PERTENECE EL USUARIO Y DEVOLVER NOTAS DE TODOS ELLOS?
	// usado en 2 metodos q se llaman en SakaiGrades.js  que se llama en el dashboard solamente
                    ToolConfiguration tc = null;
                    Collection<ToolConfiguration> gbs = site.getTools("sakai.gradebookng");
                    for (ToolConfiguration tool : gbs) {
                        System.out.println("15");
                        Properties props = tool.getPlacementConfig();
                        if (props.getProperty("gb-group") == null) {
                            // S2U-26 leaving backwards compatibility for the moment, it will always be a site=id situation until SAK-49493 is completed
                            tc = tool;
                            break;
                        }
                    }
                    System.out.println("16");
                    url = tc != null ? "/portal/directtool/" + tc.getId() : "";
                }
                bean.setUrl(url);

                // add data to list
                beans.add(bean);
                System.out.println("17");
            }
            return beans;
        }).orElse(Collections.emptyList());
    };

    @GetMapping(value = "/users/me/grades", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<GradeRestBean> getUserGrades() {
        System.out.println("18");

        Session session = checkSakaiSession();
        return portalService.getPinnedSites(session.getUserId()).stream()
                .flatMap(s -> gradeDataSupplierForSite.apply(s).stream())
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/sites/{siteId}/grades", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<GradeRestBean> getSiteGrades(@PathVariable String siteId) throws UserNotDefinedException {
        System.out.println("19");

        checkSakaiSession();

        return gradeDataSupplierForSite.apply(siteId);
    }

    @GetMapping(value = "/sites/{siteId}/items", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<GradebookRestBean> getSiteItems(@PathVariable String siteId) throws UserNotDefinedException {
System.out.println("getSiteItems");
        checkSakaiSession();

        List<GradebookRestBean> gbWithItems = new ArrayList<>();
		List<String> gradebooks = gradingService.getGradebookGroupInstances(siteId);
		for (String gradebookUid : gradebooks) {
log.info("gradebookUid " + gradebookUid);
			List<GradebookItemRestBean> gbItems = new ArrayList<>();
			List<Assignment> gradebookAssignments = gradingService.getAssignments(gradebookUid, siteId, SortType.SORT_BY_NONE);
			for (Assignment gAssignment : gradebookAssignments) {
//TODO revisar - esta comprobacion ahora mismo viene de tareas, faltan otras y luego cada tool tendra
				if (!gAssignment.getExternallyMaintained() || gAssignment.getExternallyMaintained() && gAssignment.getExternalAppName().equals(AssignmentServiceConstants.ASSIGNMENT_TOOL_ID)) {

					// gradebook item has been associated or not
					String gaId = gAssignment.getExternallyMaintained() ? gAssignment.getExternalId() : gAssignment.getId().toString();
log.info("gaId " + gaId);
					// gradebook assignment label
					String label = gAssignment.getName();
					//gradebookAssignmentsLabel.put(formattedText.escapeHtml(gaId), label);
					
					GradebookItemRestBean itemDto = new GradebookItemRestBean(gaId, label, false);
					gbItems.add(itemDto);
				}
else log.info("-----no");
			}
			// TODO obtener nombre para el guid
			GradebookRestBean gbDto = new GradebookRestBean(gradebookUid, "Nombre " + gradebookUid, gbItems);
            gbWithItems.add(gbDto);
        }

        return gbWithItems;

    }

}
