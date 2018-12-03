package co.wishkeeper.server.search

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server._
import co.wishkeeper.server.projections.Projection

trait UserSearchProjection {
  def byName(userId: UUID, query: String): UserSearchResults
}

class SimpleScanUserSearchProjection(dataStore: DataStore) extends UserSearchProjection with EventProcessor with Projection {

  override def rebuild(): Unit = {
    dataStore.truncateUserByName()
    dataStore.userEmails.foreach(pair => {
      val userId = pair._2
      val user = User.replay(dataStore.userEvents(userId))
      val profile = user.userProfile
      profile.name.foreach(name => dataStore.saveUserByName(UserNameSearchRow(userId, name, profile.picture, profile.firstName, profile.lastName)))
    })
  }

  def byName(userId: UUID, query: String): UserSearchResults = {
    val friends = User.replay(dataStore.userEvents(userId)).friends.current.toSet
    val searchTerms = query.toLowerCase().split("\\s")
    val matches = dataStore.userNames().foldLeft[List[(UserNameSearchRow, Int)]](Nil) {
      case (list, row) if searchTerms.forall(s => row.name.toLowerCase().contains(s)) => list :+ (row, calcRank(row, searchTerms))
      case (list, _) => list
    }
    UserSearchResults(matches.sortBy(-_._2).map {
      case (row, _) =>
        val resultUserId = row.userId
        UserSearchResult(resultUserId, row.name, row.picture, row.firstName, friends.contains(resultUserId))
    })
  }

  private def calcRank(row: UserNameSearchRow, terms: Array[String]): Int = terms.map(calcRank(row, _)).sum

  private def calcRank(row: UserNameSearchRow, query: String) = {
    val firstNameRank = row.firstName.map(_.toLowerCase().indexOf(query)).getOrElse(-1)
    val lastNameRank = row.lastName.map(_.toLowerCase().indexOf(query)).getOrElse(-1)
    if (firstNameRank >= 0)
      10000 - firstNameRank
    else if (lastNameRank >= 0)
      1000 - lastNameRank
    else
      500 - row.name.toLowerCase().indexOf(query)
  }

  override def process[E <: UserEvent](instance: UserEventInstance[E]): List[UserEventInstance[_ <: UserEvent]] = {
    val userId = instance.userId
    instance.event match {
      case UserNameSet(_, name) =>
        doIfValidUser(userId, user =>
          UserNameSearchRow(userId, name, user.userProfile.picture, user.userProfile.firstName, user.userProfile.lastName))
      case UserFirstNameSet(_, name) =>
        doIfValidUser(userId, user => {
          val fullName = user.userProfile.name.getOrElse(name)
          UserNameSearchRow(userId, fullName, user.userProfile.picture, Option(name), user.userProfile.lastName)
        })
      case UserLastNameSet(_, name) =>
        doIfValidUser(userId, user => {
          val fullName = user.userProfile.name.getOrElse(name)
          UserNameSearchRow(userId, fullName, user.userProfile.picture, user.userProfile.firstName, Option(name))
        })
      case UserPictureSet(_, link) =>
        doIfValidUser(userId, user =>
          UserNameSearchRow(userId, user.userProfile.name.get, Option(link), user.userProfile.firstName, user.userProfile.lastName))
      case _ =>
    }
    Nil
  }

  private def doIfValidUser(userId: UUID, row: User => UserNameSearchRow) = {
    val user = User.replay(dataStore.userEvents(userId))
    if (user.flags.everConnected) {
      dataStore.saveUserByName(row(user))
    }
  }
}
