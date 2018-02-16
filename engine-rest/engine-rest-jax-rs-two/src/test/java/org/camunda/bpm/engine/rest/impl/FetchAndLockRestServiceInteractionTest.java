/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.rest.impl;

import com.jayway.restassured.http.ContentType;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.externaltask.ExternalTaskQueryTopicBuilder;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.TenantQuery;
import org.camunda.bpm.engine.impl.digest._apacheCommonsCodec.Base64;
import org.camunda.bpm.engine.rest.AbstractRestServiceTest;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.rest.helper.MockProvider;
import org.camunda.bpm.engine.rest.impl.fetchAndLock.FetchAndLockHandler;
import org.camunda.bpm.engine.rest.impl.fetchAndLock.FetchExternalTasksExtendedDto;
import org.camunda.bpm.engine.rest.util.container.TestContainerRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.InOrder;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response.Status;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Tassilo Weidner
 */
public class FetchAndLockRestServiceInteractionTest extends AbstractRestServiceTest {

  @ClassRule
  public static TestContainerRule rule = new TestContainerRule();

  private static final String FETCH_EXTERNAL_TASK_URL =  "/external-task/fetchAndLock";
  private static final String FETCH_EXTERNAL_TASK_URL_NAMED_ENGINE =  NamedProcessEngineRestServiceImpl.PATH + "/{name}" + FETCH_EXTERNAL_TASK_URL;

  private ExternalTaskService externalTaskService;
  private ExternalTaskQueryTopicBuilder fetchTopicBuilder;
  private LockedExternalTask lockedExternalTaskMock;

  private IdentityService identityServiceMock;

  @Before
  public void setUpRuntimeData() {
    externalTaskService = mock(ExternalTaskService.class);
    when(processEngine.getExternalTaskService()).thenReturn(externalTaskService);
    fetchTopicBuilder = mock(ExternalTaskQueryTopicBuilder.class);
    lockedExternalTaskMock = MockProvider.createMockLockedExternalTask();
    when(externalTaskService.fetchAndLock(anyInt(), any(String.class), any(Boolean.class))).thenReturn(fetchTopicBuilder);
    when(fetchTopicBuilder.topic(any(String.class), anyLong())).thenReturn(fetchTopicBuilder);
    when(fetchTopicBuilder.variables(anyListOf(String.class))).thenReturn(fetchTopicBuilder);
    when(fetchTopicBuilder.enableCustomObjectDeserialization()).thenReturn(fetchTopicBuilder);

    // for authentication
    identityServiceMock = mock(IdentityService.class);
    when(processEngine.getIdentityService()).thenReturn(identityServiceMock);

    List<Group> groupMocks = MockProvider.createMockGroups();
    setupGroupQueryMock(groupMocks);

    List<Tenant> tenantMocks = Collections.singletonList(MockProvider.createMockTenant());
    setupTenantQueryMock(tenantMocks);

    when(identityServiceMock.checkPassword(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)).thenReturn(true);
  }

  @Test
  public void shouldFetchAndLock() {
    List<LockedExternalTask> tasks = new ArrayList<LockedExternalTask>(Collections.singleton(lockedExternalTaskMock));
    when(fetchTopicBuilder.execute()).thenReturn(tasks);
    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(null, true, true, false);

    given()
      .auth().preemptive().basic(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
      .pathParam("name", "default")
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("[0].id", equalTo(MockProvider.EXTERNAL_TASK_ID))
      .body("[0].topicName", equalTo(MockProvider.EXTERNAL_TASK_TOPIC_NAME))
      .body("[0].workerId", equalTo(MockProvider.EXTERNAL_TASK_WORKER_ID))
      .body("[0].lockExpirationTime", equalTo(MockProvider.EXTERNAL_TASK_LOCK_EXPIRATION_TIME))
      .body("[0].processInstanceId", equalTo(MockProvider.EXAMPLE_PROCESS_INSTANCE_ID))
      .body("[0].executionId", equalTo(MockProvider.EXAMPLE_EXECUTION_ID))
      .body("[0].activityId", equalTo(MockProvider.EXAMPLE_ACTIVITY_ID))
      .body("[0].activityInstanceId", equalTo(MockProvider.EXAMPLE_ACTIVITY_INSTANCE_ID))
      .body("[0].processDefinitionId", equalTo(MockProvider.EXAMPLE_PROCESS_DEFINITION_ID))
      .body("[0].processDefinitionKey", equalTo(MockProvider.EXAMPLE_PROCESS_DEFINITION_KEY))
      .body("[0].tenantId", equalTo(MockProvider.EXAMPLE_TENANT_ID))
      .body("[0].retries", equalTo(MockProvider.EXTERNAL_TASK_RETRIES))
      .body("[0].errorMessage", equalTo(MockProvider.EXTERNAL_TASK_ERROR_MESSAGE))
      .body("[0].errorMessage", equalTo(MockProvider.EXTERNAL_TASK_ERROR_MESSAGE))
      .body("[0].priority", equalTo(MockProvider.EXTERNAL_TASK_PRIORITY))
      .body("[0].variables." + MockProvider.EXAMPLE_VARIABLE_INSTANCE_NAME,
        notNullValue())
      .body("[0].variables." + MockProvider.EXAMPLE_VARIABLE_INSTANCE_NAME + ".value",
        equalTo(MockProvider.EXAMPLE_PRIMITIVE_VARIABLE_VALUE.getValue()))
      .body("[0].variables." + MockProvider.EXAMPLE_VARIABLE_INSTANCE_NAME + ".type",
        equalTo("String"))
    .when().post(FETCH_EXTERNAL_TASK_URL_NAMED_ENGINE);

    InOrder inOrder = inOrder(fetchTopicBuilder, externalTaskService);
    inOrder.verify(externalTaskService).fetchAndLock(5, "aWorkerId", true);
    inOrder.verify(fetchTopicBuilder).topic("aTopicName", 12354L);
    inOrder.verify(fetchTopicBuilder).variables(Collections.singletonList(MockProvider.EXAMPLE_VARIABLE_INSTANCE_NAME));
    inOrder.verify(fetchTopicBuilder).execute();
    verifyNoMoreInteractions(fetchTopicBuilder, externalTaskService);
  }

  @Test
  public void shouldFetchWithoutVariables() {
    List<LockedExternalTask> tasks = new ArrayList<LockedExternalTask>(Collections.singleton(lockedExternalTaskMock));
    when(fetchTopicBuilder.execute()).thenReturn(tasks);
    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(null);

    given()
      .auth().preemptive().basic(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
    .then()
      .expect()
      .statusCode(Status.OK.getStatusCode())
      .body("[0].id", equalTo(MockProvider.EXTERNAL_TASK_ID))
    .when()
      .post(FETCH_EXTERNAL_TASK_URL);

    InOrder inOrder = inOrder(fetchTopicBuilder, externalTaskService);
    inOrder.verify(externalTaskService).fetchAndLock(5, "aWorkerId", false);
    inOrder.verify(fetchTopicBuilder).topic("aTopicName", 12354L);
    inOrder.verify(fetchTopicBuilder).execute();
    verifyNoMoreInteractions(fetchTopicBuilder, externalTaskService);
  }

  @Test
  public void shouldFetchWithCustomObjectDeserializationEnabled() {
    List<LockedExternalTask> tasks = new ArrayList<LockedExternalTask>(Collections.singleton(lockedExternalTaskMock));
    when(fetchTopicBuilder.execute()).thenReturn(tasks);
    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(null, false, true, true);

    given()
      .auth().preemptive().basic(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
      .pathParam("name", "default")
    .then()
      .expect()
      .statusCode(Status.OK.getStatusCode())
    .when()
      .post(FETCH_EXTERNAL_TASK_URL_NAMED_ENGINE);

    InOrder inOrder = inOrder(fetchTopicBuilder, externalTaskService);
    inOrder.verify(externalTaskService).fetchAndLock(5, "aWorkerId", false);
    inOrder.verify(fetchTopicBuilder).topic("aTopicName", 12354L);
    inOrder.verify(fetchTopicBuilder).variables(Collections.singletonList(MockProvider.EXAMPLE_VARIABLE_INSTANCE_NAME));
    inOrder.verify(fetchTopicBuilder).enableCustomObjectDeserialization();
    inOrder.verify(fetchTopicBuilder).execute();
    verifyNoMoreInteractions(fetchTopicBuilder, externalTaskService);
  }

  @Test
  public void shouldThrowInvalidRequestExceptionOnLessThanMinTimeout() {
    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(100L);

    given()
      .auth().preemptive().basic(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
    .then()
      .expect()
        .body("type", equalTo(InvalidRequestException.class.getSimpleName()))
        .body("message", containsString("The asynchronous response timeout cannot be set to a value less than "))
        .statusCode(Status.BAD_REQUEST.getStatusCode())
    .when()
      .post(FETCH_EXTERNAL_TASK_URL);
  }

  @Test
  public void shouldThrowInvalidRequestExceptionOnMaxTimeoutExceeded() {
    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(FetchAndLockHandler.MAX_TIMEOUT + 1);

    given()
      .auth().preemptive().basic(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
      .pathParam("name", "default")
    .then()
      .expect()
        .body("type", equalTo(InvalidRequestException.class.getSimpleName()))
        .body("message", containsString("The asynchronous response timeout cannot be set to a value greater than "))
        .statusCode(Status.BAD_REQUEST.getStatusCode())
    .when()
      .post(FETCH_EXTERNAL_TASK_URL_NAMED_ENGINE);
  }

  @Test
  public void shouldThrowProcessEngineException() {
    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(null);

    doThrow(new ProcessEngineException("anExceptionMessage"))
      .when(externalTaskService).fetchAndLock(fetchExternalTasksDto.getMaxTasks(), fetchExternalTasksDto.getWorkerId(),
          fetchExternalTasksDto.isUsePriority());

    given()
      .auth().preemptive().basic(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
      .pathParam("name", "default")
    .then()
      .expect()
        .body("type", equalTo(InvalidRequestException.class.getSimpleName()))
        .body("message", equalTo("anExceptionMessage"))
        .statusCode(Status.BAD_REQUEST.getStatusCode())
    .when()
      .post(FETCH_EXTERNAL_TASK_URL_NAMED_ENGINE);
  }

  @Test
  public void shouldResponseImmediatelyDueToAvailableTasks() {
    List<LockedExternalTask> tasks = new ArrayList<LockedExternalTask>(Collections.singleton(lockedExternalTaskMock));
    when(fetchTopicBuilder.execute()).thenReturn(tasks);

    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(FetchAndLockHandler.MIN_TIMEOUT);

    given()
      .auth().preemptive().basic(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
    .then()
      .expect()
        .body("size()", is(1))
        .statusCode(Status.OK.getStatusCode())
    .when()
      .post(FETCH_EXTERNAL_TASK_URL);
  }
  
  @Test
  public void shouldDeclineRequestDueToInvalidAuthorization() {
    when(identityServiceMock.checkPassword(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)).thenReturn(false);
    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(FetchAndLockHandler.MIN_TIMEOUT);

    given()
      .auth().preemptive().basic(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
      .pathParam("name", "default")
    .then()
      .expect()
        .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"default\"")
        .body(isEmptyString())
        .statusCode(Status.UNAUTHORIZED.getStatusCode())
    .when()
      .post(FETCH_EXTERNAL_TASK_URL_NAMED_ENGINE);
  }

  @Test
  public void shouldAcceptRequestDueToValidAuthorization() {
    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(FetchAndLockHandler.MIN_TIMEOUT);

    given()
      .auth().preemptive().basic(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
      .pathParam("name", "default")
    .then()
      .expect()
        .body("isEmpty()", is(true))
        .statusCode(Status.OK.getStatusCode())
    .when()
      .post(FETCH_EXTERNAL_TASK_URL_NAMED_ENGINE);
  }

  @Test
  public void shouldFailByAuthenticationCheck() {
    when(identityServiceMock.checkPassword(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)).thenReturn(false);
    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(FetchAndLockHandler.MIN_TIMEOUT);

    given()
      .auth().preemptive().basic(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)
      .pathParam("name", "default")
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
    .then().expect()
      .statusCode(Status.UNAUTHORIZED.getStatusCode())
      .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"default\"")
    .when().post(FETCH_EXTERNAL_TASK_URL_NAMED_ENGINE);
  }

  @Test
  public void shouldFailByMissingAuthHeader() {
    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(FetchAndLockHandler.MIN_TIMEOUT);
    given()
      .pathParam("name", "someengine")
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
    .then().expect()
      .statusCode(Status.UNAUTHORIZED.getStatusCode())
      .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"someengine\"")
    .when().post(FETCH_EXTERNAL_TASK_URL_NAMED_ENGINE);
  }

  @Test
  public void shouldFailByMalformedCredentials() {
    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(FetchAndLockHandler.MIN_TIMEOUT);
    given()
      .header(HttpHeaders.AUTHORIZATION, "Basic " + new String(Base64.encodeBase64("this is not a valid format".getBytes())))
      .pathParam("name", "default")
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
    .then().expect()
      .statusCode(Status.UNAUTHORIZED.getStatusCode())
      .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"default\"")
    .when().post(FETCH_EXTERNAL_TASK_URL_NAMED_ENGINE);
  }

  @Test
  public void shouldFailByMalformedBase64Value() {
    FetchExternalTasksExtendedDto fetchExternalTasksDto = createDto(FetchAndLockHandler.MIN_TIMEOUT);
    given()
      .header(HttpHeaders.AUTHORIZATION, "Basic someNonBase64Characters!(#")
      .pathParam("name", "default")
      .contentType(ContentType.JSON)
      .body(fetchExternalTasksDto)
    .then().expect()
      .statusCode(Status.UNAUTHORIZED.getStatusCode())
      .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"default\"")
    .when().post(FETCH_EXTERNAL_TASK_URL_NAMED_ENGINE);
  }
  
  // helper /////////////////////////

  private FetchExternalTasksExtendedDto createDto(Long responseTimeout) {
    return createDto(responseTimeout, false, false, false);
  }
  
  private FetchExternalTasksExtendedDto createDto(Long responseTimeout, boolean usePriority, boolean withVariables, boolean withDeserialization) {
    FetchExternalTasksExtendedDto fetchExternalTasksDto = new FetchExternalTasksExtendedDto();
    if (responseTimeout != null) {
      fetchExternalTasksDto.setAsyncResponseTimeout(responseTimeout);
    }
    fetchExternalTasksDto.setMaxTasks(5);
    fetchExternalTasksDto.setWorkerId("aWorkerId");
    fetchExternalTasksDto.setUsePriority(usePriority);
    FetchExternalTasksExtendedDto.FetchExternalTaskTopicDto topicDto = new FetchExternalTasksExtendedDto.FetchExternalTaskTopicDto();
    fetchExternalTasksDto.setTopics(Collections.singletonList(topicDto));
    topicDto.setTopicName("aTopicName");
    topicDto.setLockDuration(12354L);
    if (withVariables) {
      topicDto.setVariables(Collections.singletonList(MockProvider.EXAMPLE_VARIABLE_INSTANCE_NAME));
    }
    topicDto.setDeserializeValues(withDeserialization);
    fetchExternalTasksDto.setTopics(Collections.singletonList(topicDto));
    return fetchExternalTasksDto;
  }

  private void setupGroupQueryMock(List<Group> groups) {
    GroupQuery mockGroupQuery = mock(GroupQuery.class);

    when(identityServiceMock.createGroupQuery()).thenReturn(mockGroupQuery);
    when(mockGroupQuery.groupMember(anyString())).thenReturn(mockGroupQuery);
    when(mockGroupQuery.list()).thenReturn(groups);
  }

  private void setupTenantQueryMock(List<Tenant> tenants) {
    TenantQuery mockTenantQuery = mock(TenantQuery.class);

    when(identityServiceMock.createTenantQuery()).thenReturn(mockTenantQuery);
    when(mockTenantQuery.userMember(anyString())).thenReturn(mockTenantQuery);
    when(mockTenantQuery.includingGroupsOfUser(anyBoolean())).thenReturn(mockTenantQuery);
    when(mockTenantQuery.list()).thenReturn(tenants);
  }

}