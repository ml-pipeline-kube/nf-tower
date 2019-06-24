/*
 * Copyright (c) 2019, Seqera Labs.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.watchtower.service

import grails.gorm.transactions.Transactional
import groovy.transform.CompileDynamic
import io.seqera.watchtower.domain.SummaryEntry
import io.seqera.watchtower.domain.Task
import io.seqera.watchtower.domain.User
import io.seqera.watchtower.domain.Workflow
import io.seqera.watchtower.pogo.exceptions.NonExistingWorkflowException
import io.seqera.watchtower.pogo.exchange.trace.TraceWorkflowRequest

import javax.inject.Inject
import javax.inject.Singleton

@Transactional
@Singleton
class WorkflowServiceImpl implements WorkflowService {

    ProgressService progressService

    @Inject
    WorkflowServiceImpl(ProgressService progressService) {
        this.progressService = progressService
    }

    @CompileDynamic
    Workflow get(Serializable id) {
        Workflow.get(id)
    }

    @CompileDynamic
    List<Workflow> list(User owner) {
        Workflow.findAllByOwner(owner, [sort: 'start', order: 'desc'])
    }

    Workflow processWorkflowJsonTrace(TraceWorkflowRequest traceWorkflowRequest, User owner) {
        traceWorkflowRequest.workflow.checkIsStarted() ? createFromJson(traceWorkflowRequest.workflow, owner) : updateFromJson(traceWorkflowRequest.workflow, traceWorkflowRequest.summary)
    }

    private Workflow createFromJson(Workflow workflow, User owner) {
        workflow.submit = workflow.start

        workflow.owner = owner
        workflow.save()
        workflow
    }

    @CompileDynamic
    private Workflow updateFromJson(Workflow workflow, List<SummaryEntry> summary) {
        Workflow existingWorkflow = Workflow.get(workflow.workflowId)
        if (!existingWorkflow) {
            throw new NonExistingWorkflowException("Can't update a non-existing workflow")
        }

        updateChangeableFields(existingWorkflow, workflow)
        associateSummaryEntries(existingWorkflow, summary)
        associateProgress(existingWorkflow)

        existingWorkflow.save()
        existingWorkflow
    }

    private void updateChangeableFields(Workflow workflowToUpdate, Workflow originalWorkflow) {
        workflowToUpdate.resume = originalWorkflow.resume
        workflowToUpdate.success = originalWorkflow.success
        workflowToUpdate.complete = originalWorkflow.complete
        workflowToUpdate.duration = originalWorkflow.duration

        workflowToUpdate.exitStatus = originalWorkflow.exitStatus
        workflowToUpdate.errorMessage = originalWorkflow.errorMessage
        workflowToUpdate.errorReport = originalWorkflow.errorReport

        workflowToUpdate.stats = originalWorkflow.stats
    }

    private void associateSummaryEntries(Workflow workflow, List<SummaryEntry> summary) {
        summary.each { SummaryEntry summaryEntry ->
            workflow.addToSummaryEntries(summaryEntry)
        }
    }

    private void associateProgress(Workflow workflow) {
        if (!workflow.checkIsStarted()) {
            workflow.progress = progressService.computeProgress(workflow.id)
        }
    }

    void delete(Workflow workflow) {
        workflow.tasks?.each { Task task ->
            task.delete()
        }
        workflow.summaryEntries?.each { SummaryEntry summaryEntry ->
            summaryEntry.delete()
        }

        workflow.delete()
    }

}