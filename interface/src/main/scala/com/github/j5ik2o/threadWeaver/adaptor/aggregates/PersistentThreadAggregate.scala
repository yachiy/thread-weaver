package com.github.j5ik2o.threadWeaver.adaptor.aggregates

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import com.github.j5ik2o.threadWeaver.domain.model.threads.{ Messages, Thread, ThreadId }
import com.github.j5ik2o.threadWeaver.infrastructure.ulid.ULID
import com.github.j5ik2o.threadWeaver.adaptor.aggregates.ThreadProtocol._

object PersistentThreadAggregate {

  sealed trait State
  case object EmptyState                  extends State
  case class DefinedState(thread: Thread) extends State

  def behavior(id: ThreadId): Behavior[CommandRequest] =
    Behaviors.setup[CommandRequest] { ctx =>
      EventSourcedBehavior[CommandRequest, Event, State](
        persistenceId = PersistenceId("P-" + id.value.asString),
        emptyState = EmptyState,
        commandHandler = {
          case (EmptyState, c @ CreateThread(requestId, threadId, _, _, _, _, createAt, replyTo)) =>
            Effect.persist(c.toEvent).thenRun { _ =>
              replyTo.foreach(_ ! CreateThreadSucceeded(ULID(), requestId, threadId, createAt))
            }

          case (
              DefinedState(thread),
              c @ AddAdministratorIds(requestId, threadId, senderId, administratorIds, createAt, replyTo)
              ) =>
            Effect.persist(c.toEvent).thenRun { _ =>
              thread.addAdministratorIds(administratorIds, senderId) match {
                case Left(exception) =>
                  replyTo.foreach(
                    _ ! AddAdministratorIdsFailed(ULID(), requestId, threadId, exception.getMessage, createAt)
                  )
                case Right(_) =>
                  replyTo.foreach(_ ! AddAdministratorIdsSucceeded(ULID(), requestId, threadId, createAt))
              }
            }

          case (DefinedState(thread), c @ AddMemberIds(requestId, threadId, senderId, memberIds, createAt, replyTo)) =>
            Effect.persist(c.toEvent).thenRun { _ =>
              thread.addMemberIds(memberIds, senderId) match {
                case Left(exception) =>
                  replyTo.foreach(
                    _ ! AddMemberIdsFailed(ULID(), requestId, threadId, exception.getMessage, createAt)
                  )
                case Right(_) =>
                  replyTo.foreach(_ ! AddMemberIdsSucceeded(ULID(), requestId, threadId, createAt))
              }
            }

          case (DefinedState(thread), c @ AddMessages(requestId, threadId, senderId, messages, createAt, replyTo)) =>
            Effect.persist(c.toEvent).thenRun { _ =>
              thread.addMessages(messages, senderId, createAt) match {
                case Left(exception) =>
                  replyTo.foreach(_ ! AddMessagesFailed(ULID(), requestId, threadId, exception.getMessage, createAt))
                case Right(_) =>
                  replyTo.foreach(_ ! AddMessagesSucceeded(ULID(), requestId, threadId, createAt))
              }
            }

          case (DefinedState(thread), GetMessages(requestId, threadId, senderId, createAt, replyTo)) =>
            Effect.none.thenRun { _ =>
              if (thread.isMemberId(senderId))
                replyTo ! GetMessagesSucceeded(ULID(), requestId, threadId, thread.messages, createAt)
              else
                replyTo ! GetMessagesFailed(ULID(), requestId, threadId, "", createAt)
            }

          case _ =>
            Effect.none

        },
        eventHandler = {
          case (EmptyState, e: ThreadCreated) =>
            DefinedState(
              Thread(
                e.threadId,
                e.parentThreadId,
                e.administratorIds,
                e.memberIds,
                Messages.empty,
                e.createdAt,
                e.createdAt
              )
            )
          case (DefinedState(thread), e: AdministratorIdsAdded) =>
            DefinedState(
              thread.addAdministratorIds(e.administratorIds, e.senderId).right.get
            )
          case (DefinedState(thread), e: MemberIdsAdded) =>
            DefinedState(
              thread.addMemberIds(e.memberIds, e.senderId).right.get
            )
          case (DefinedState(thread), e: MessagesAdded) =>
            DefinedState(
              thread.addMessages(e.messages, e.senderId, e.createdAt).right.get
            )
          case (state, _) =>
            state

        }
      )
    }
}
