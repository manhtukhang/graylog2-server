/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.rest.resources.dashboards;

import com.google.common.eventbus.EventBus;
import org.apache.shiro.subject.Subject;
import org.graylog2.dashboards.Dashboard;
import org.graylog2.dashboards.DashboardService;
import org.graylog2.events.ClusterEventBus;
import org.graylog2.plugin.database.users.User;
import org.graylog2.rest.models.dashboards.requests.CreateDashboardRequest;
import org.graylog2.shared.bindings.GuiceInjectorHolder;
import org.graylog2.shared.system.activities.ActivityWriter;
import org.graylog2.shared.users.UserService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import javax.ws.rs.core.UriBuilder;
import java.security.Principal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class DashboardsResourceTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private DashboardService dashboardService;
    @Mock
    private ActivityWriter activityWriter;
    @Mock
    private ClusterEventBus clusterEventBus;
    @Mock
    private EventBus serverEventBus;
    @Mock
    private Subject subject;
    @Mock
    private Principal principal;
    @Mock
    private UserService userService;
    @Mock
    private User user;

    private DashboardsTestResource dashboardsResource;

    private static class DashboardsTestResource extends DashboardsResource {
        private final Subject subject;

        DashboardsTestResource(DashboardService dashboardService,
                               ActivityWriter activityWriter,
                               ClusterEventBus clusterEventBus,
                               EventBus serverEventBus,
                               Subject subject,
                               UserService userService) {
            super(dashboardService, activityWriter, clusterEventBus, serverEventBus);
            this.subject = subject;
            this.userService = userService;
        }

        @Override
        protected void checkPermission(String permission) {
        }

        @Override
        protected void checkPermission(String permission, String instanceId) {
        }

        @Override
        protected void checkAnyPermission(String[] permissions, String instanceId) {
        }

        @Override
        protected Subject getSubject() {
            return subject;
        }

        @Override
        protected UriBuilder getUriBuilderToSelf() {
            return UriBuilder.fromUri("http://testserver/api");
        }
    }

    public DashboardsResourceTest() {
        GuiceInjectorHolder.createInjector(Collections.emptyList());
    }

    @Before
    public void setUp() throws Exception {
        final String testuserName = "testuser";
        when(subject.getPrincipal()).thenReturn(principal);
        when(principal.toString()).thenReturn(testuserName);
        when(userService.load(eq(testuserName))).thenReturn(user);
        when(user.getName()).thenReturn(testuserName);

        this.dashboardsResource = new DashboardsTestResource(dashboardService, activityWriter, clusterEventBus, serverEventBus, subject, userService);
    }

    @Test
    public void creatingADashboardAddsRequiredPermissionsForNonAdmin() throws Exception {
        when(subject.isPermitted(anyString())).thenReturn(false);
        final Dashboard dashboard = mock(Dashboard.class);
        when(dashboardService.create(eq("foo"), eq("bar"), anyString(), ArgumentMatchers.any())).thenReturn(dashboard);

        final String dashboardId = "dashboardId";
        when(dashboardService.save(dashboard)).thenReturn(dashboardId);

        this.dashboardsResource.create(CreateDashboardRequest.create("foo", "bar"));

        verify(userService, times(2)).save(user);
        final ArgumentCaptor<String> ensurePermissionsArguments = ArgumentCaptor.forClass(String.class);

        verify(user, times(2)).ensurePermission(ensurePermissionsArguments.capture());

        assertThat(ensurePermissionsArguments.getAllValues())
                .isNotNull()
                .isNotEmpty()
                .containsExactly(
                        "dashboards:read:" + dashboardId,
                        "dashboards:edit:" + dashboardId
                );
    }
    @Test
    public void creatingADashboardDoesNotAddPermissionsForAdmin() throws Exception {
        when(subject.isPermitted(anyString())).thenReturn(true);
        final Dashboard dashboard = mock(Dashboard.class);
        when(dashboardService.create(eq("foo"), eq("bar"), anyString(), ArgumentMatchers.any())).thenReturn(dashboard);

        final String dashboardId = "dashboardId";
        when(dashboardService.save(dashboard)).thenReturn(dashboardId);

        this.dashboardsResource.create(CreateDashboardRequest.create("foo", "bar"));

        verify(userService, never()).save(user);

        verify(user, never()).ensurePermission(anyString());
    }
}