package co.wishkeeper.server.search

import java.util.UUID

case class SearchQuery(query: String)

case class UserSearchResults(users: List[UserSearchResult] = Nil)

case class UserSearchResult(userId: UUID, name: String, pic: Option[String] = None, firstName: Option[String] = None, isDirectFriend: Boolean = false)