import { describe } from '../k6chaijs.js'
import { Holder, Issuer } from '../actors'

export const issuer = new Issuer()
export const holder = new Holder()

export function connectionFlow () {
  describe('Issuer initiates connection with Holder', function () {
    issuer.createHolderConnection()
  }) &&

  describe('Holder accepts connection with Issuer', function () {
    holder.acceptIssuerConnection(issuer.connectionWithHolder!.invitation)
  }) &&

  describe('Issuer finalizes connection with Holder', function () {
    issuer.finalizeConnectionWithHolder()
  }) &&

  describe('Holder finalizes connection with Issuer', function () {
    holder.finalizeConnectionWithIssuer()
  })
}
