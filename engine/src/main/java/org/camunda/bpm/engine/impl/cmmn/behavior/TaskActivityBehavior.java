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
package org.camunda.bpm.engine.impl.cmmn.behavior;

import static org.camunda.bpm.engine.impl.cmmn.execution.CaseExecutionState.ACTIVE;
import static org.camunda.bpm.engine.impl.cmmn.execution.CaseExecutionState.FAILED;

import org.camunda.bpm.engine.impl.cmmn.execution.CmmnActivityExecution;

/**
 * @author Roman Smirnov
 *
 */
public class TaskActivityBehavior extends StageOrTaskActivityBehavior {

  public void onReactivation(CmmnActivityExecution execution) {
    ensureTransitionAllowed(execution, FAILED, ACTIVE, "re-activate");
  }

  public void started(CmmnActivityExecution execution) throws Exception {
    execution.complete();
  }

}
